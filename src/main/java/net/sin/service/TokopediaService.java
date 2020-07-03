package net.sin.service;

import net.sin.model.ProductResult;
import net.sin.model.ShopResult;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public interface TokopediaService {
    List<String> getSuggestion(String term) throws IOException, InterruptedException;

    Collection<ShopResult> getShopSearchResult(String query) throws IOException, InterruptedException;

    Collection<ProductResult> getProductSearchResult(long store, String query) throws IOException, InterruptedException;
}
