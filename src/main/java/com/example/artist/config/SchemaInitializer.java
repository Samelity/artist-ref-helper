package com.example.artist.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 补齐 Hibernate ddl-auto 表达不了的 schema 对象。
 * <p>
 * content_hash 的部分唯一索引把「先查后插」的去重升级为数据库级保证：
 * 并发入库时后到的一方撞索引，由 Python 侧当作已存在跳过。
 * 索引建不起来时只降级告警，不阻断启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaInitializer implements ApplicationRunner {

    /** 索引名与 python-service/db.py 的 CONTENT_HASH_INDEX 一致，Python 靠它识别冲突来源。 */
    private static final String CONTENT_HASH_INDEX = "ux_reference_image_content_hash";

    private static final String CREATE_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS " + CONTENT_HASH_INDEX
                    + " ON reference_image (content_hash) WHERE content_hash IS NOT NULL";

    private static final String COUNT_DUPLICATE_HASHES_SQL =
            "SELECT count(*) FROM (SELECT content_hash FROM reference_image "
                    + "WHERE content_hash IS NOT NULL GROUP BY content_hash HAVING count(*) > 1) d";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(CREATE_INDEX_SQL);
            log.info("content_hash 唯一索引已就绪: {}", CONTENT_HASH_INDEX);
        } catch (DuplicateKeyException e) {
            Long groups = jdbcTemplate.queryForObject(COUNT_DUPLICATE_HASHES_SQL, Long.class);
            log.warn("content_hash 存在 {} 组重复，唯一索引未创建，去重退回先查后插；"
                    + "清理重复图片后重启即可生效", groups, e);
        } catch (DataAccessException e) {
            log.warn("content_hash 唯一索引创建失败，去重退回先查后插", e);
        }
    }
}