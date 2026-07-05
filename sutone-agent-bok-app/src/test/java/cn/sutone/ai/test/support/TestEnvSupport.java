package cn.sutone.ai.test.support;

public final class TestEnvSupport {

    private TestEnvSupport() {
    }

    public static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.trim().isEmpty()) ? defaultValue : value;
    }

    public static String modelBaseUrl(String defaultValue) {
        return env("SUTONE_MODEL_BASE_URL", defaultValue);
    }

    public static String modelApiKey() {
        return env("SUTONE_MODEL_API_KEY", "YOUR_MODEL_API_KEY");
    }

    public static String completionsPath(String defaultValue) {
        return env("SUTONE_MODEL_COMPLETIONS_PATH", defaultValue);
    }

    public static String embeddingsPath(String defaultValue) {
        return env("SUTONE_MODEL_EMBEDDINGS_PATH", defaultValue);
    }

    public static String modelName(String defaultValue) {
        return env("SUTONE_MODEL_NAME", defaultValue);
    }

    public static String langChainBaseUrlV1(String defaultValue) {
        return env("SUTONE_LANGCHAIN_BASE_URL_V1", defaultValue);
    }

    public static String baiduSearchBaseUri(String defaultValue) {
        return env("SUTONE_BAIDU_SEARCH_BASE_URI", defaultValue);
    }

    public static String baiduSearchSseEndpoint() {
        return env("SUTONE_BAIDU_SEARCH_SSE_ENDPOINT", "sse?api_key=YOUR_BAIDU_SEARCH_API_KEY");
    }
}
