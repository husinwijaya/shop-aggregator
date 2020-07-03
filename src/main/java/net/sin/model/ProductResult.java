package net.sin.model;

public class ProductResult extends ShopResult {
    private final String price;
    private final String image;

    public ProductResult(ECommercePlatform platform, long id, String name, String url, String price, String image) {
        super(platform, id, name, url);
        this.price = price;
        this.image = image;
    }

    public String getPrice() {
        return price;
    }

    public String getImage() {
        return image;
    }
}
