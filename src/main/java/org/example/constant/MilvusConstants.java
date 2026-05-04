package org.example.constant;

public class MilvusConstants {
    
    /**
     * Milvus 数据库名称
     */
    public static final String MILVUS_DB_NAME = "default";
    
    /**
     * Milvus 集合名称
     */
    public static final String MILVUS_COLLECTION_NAME = "biz";
    
    /**
     * 向量维度（豆包 embedding 模型的维度）
     */
    public static final int VECTOR_DIM = 1024;  // 豆包模型返回1024维向量
    
    /**
     * ID字段最大长度
     */
    public static final int ID_MAX_LENGTH = 256;
    
    /**
     * Content字段最大长度
     */
    public static final int CONTENT_MAX_LENGTH = 8192;
    
    /**
     * 默认分片数
     */
    public static final int DEFAULT_SHARD_NUMBER = 2;

    public static final String MEMORY_COLLECTION_NAME = "memory";
    public static final String MEMORY_VECTOR_FIELD = "vector";
    public static final String MEMORY_CONTENT_FIELD = "content";
    public static final String MEMORY_METADATA_FIELD = "metadata";
    public static final String MEMORY_TIMESTAMP_FIELD = "timestamp";
    public static final String MEMORY_SESSION_ID_FIELD = "session_id";
    
    private MilvusConstants() {
        // 工具类，禁止实例化
    }
}
