package net.sin.model;

import java.util.Objects;

public class ShopResult {
    private final ECommercePlatform platform;
    private final long id;
    private final String name;
    private final String url;

    public ShopResult(ECommercePlatform platform, long id, String name, String url) {
        this.platform = platform;
        this.id = id;
        this.name = name;
        this.url = url;
    }

    public ECommercePlatform getPlatform() {
        return platform;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShopResult)) return false;
        ShopResult that = (ShopResult) o;
        return id == that.id &&
                platform == that.platform;
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, id);
    }
}
