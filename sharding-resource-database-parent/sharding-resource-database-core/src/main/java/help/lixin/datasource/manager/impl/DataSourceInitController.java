package help.lixin.datasource.manager.impl;

import help.lixin.datasource.customizer.IDataSourceCustomizer;
import help.lixin.datasource.keygenerate.IKeyGenerateStrategy;
import help.lixin.datasource.manager.IDataSourceInitController;
import help.lixin.datasource.manager.store.IDataSourceStore;
import help.lixin.datasource.meta.IDataSourceMetaService;
import help.lixin.datasource.model.DatabaseResource;
import help.lixin.datasource.util.DataSourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DataSourceInitController implements IDataSourceInitController {

    private Logger logger = LoggerFactory.getLogger(DataSourceInitController.class);

    // 元数据存储中心
    private IDataSourceMetaService dataSourceMetaService;
    // id生成策略
    private IKeyGenerateStrategy keyGenerateStrategy;
    // 数据源的存储中心
    private IDataSourceStore dataSourceStore;
    // 自定义创建DataSource的详细过程
    private List<IDataSourceCustomizer> dataSourceCustomizers;

    public DataSourceInitController(IDataSourceMetaService dataSourceMetaService,
                                    IKeyGenerateStrategy keyGenerateStrategy,
                                    IDataSourceStore dataSourceStore,
                                    List<IDataSourceCustomizer> dataSourceCustomizers) {
        this.dataSourceMetaService = dataSourceMetaService;
        this.keyGenerateStrategy = keyGenerateStrategy;
        this.dataSourceStore = dataSourceStore;
        this.dataSourceCustomizers = dataSourceCustomizers;
    }

    @Override
    public void initDataSources() {
        List<DatabaseResource> metas = dataSourceMetaService.getMeta();
        if (null != metas) {
            for (DatabaseResource databaseResource : metas) {
                // 1. 初始化数据源.
                // 2. 通过IDataSourceCustomizer进行自定义.
                // 3. 保存到存储介质中心.
                initDataSource(databaseResource)
                        .ifPresent(datasource -> {
                            this.customizer(databaseResource, datasource);
                            
                            // 2. 生成key
                            String key = keyGenerateStrategy.generate(databaseResource);
                            if (logger.isInfoEnabled()) {
                                logger.info("初始化数据源名称:[{}]成功.", key);
                            }
                            dataSourceStore.register(key, datasource);
                        });
            }
        }
    }


    protected Optional<DataSource> initDataSource(DatabaseResource databaseResource) {
        String dataSourceClassName = databaseResource.getType();
        Map<String, Object> properties = databaseResource.getProperties();
        try {
            DataSource dataSource = DataSourceUtil.getDataSource(dataSourceClassName, properties);
            return Optional.of(dataSource);
        } catch (ReflectiveOperationException e) {
            logger.error("初始化DataSource:[{}]失败,失败详细内容:[{}]", databaseResource.getDriver(), e);
        }
        return Optional.empty();
    }

    protected void customizer(DatabaseResource databaseResource, DataSource dataSource) {
        if (null != dataSourceCustomizers) {
            for (IDataSourceCustomizer dataSourceCustomizer : dataSourceCustomizers) {
                // 1. 先验证是否支持
                boolean support = dataSourceCustomizer.support(dataSource);
                if (support) {
                    // 2. 才进行自定义.
                    dataSourceCustomizer.customize(databaseResource, dataSource);
                }
            }
        }
    }
}