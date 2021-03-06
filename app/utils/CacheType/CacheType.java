package utils.CacheType;

import java.util.Objects;

public enum CacheType {

    vjUsage("vjusage", true, "vjUsage"),
    spectratype("spectratype", true, "spectratype"),
    spectratypeV("spectratypev", true, "spectratypeV"),
    annotation("annotation", true,  "annotation"),
    quantileStats("quantilestats", true, "quantileStats"),
    rarefaction("rarefaction", false, "rarefaction"),
    summary("summary", false, "basicStats");

    private final String type;
    private final Boolean single;
    private final String cacheFileName;

    CacheType(String type, Boolean single, String cacheFileName) {
        this.type = type;
        this.single = single;
        this.cacheFileName = cacheFileName;
    }

    public String getType() {
        return this.type;
    }

    public Boolean getSingle() {
        return this.single;
    }

    public String getCacheFileName() {
        return this.cacheFileName;
    }

    public static CacheType findByType(String type) {
        type = type.toLowerCase();
        for (CacheType cache: CacheType.values()) {
            if (Objects.equals(cache.type, type)) {
                return cache;
            }
        }
        throw new IllegalArgumentException("Unknown cache type " + type);
    }
}
