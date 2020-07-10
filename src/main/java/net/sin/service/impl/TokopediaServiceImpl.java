package net.sin.service.impl;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import net.sin.model.ECommercePlatform;
import net.sin.model.ProductResult;
import net.sin.model.ShopResult;
import net.sin.service.TokopediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;

@Singleton
public class TokopediaServiceImpl implements TokopediaService {
    private static final Logger log = LoggerFactory.getLogger(TokopediaServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> getSuggestion(String term) throws IOException, InterruptedException {
        String body = generateSuggestionRequest(term);
        log.trace("sending body: {}", body);
        String response = post(body);
        return extractAutocomplete(response);
    }

    private String post(String requestBody) throws IOException {
        Process curl = new ProcessBuilder("curl",
                "--request",
                "POST",
                "https://gql.tokopedia.com/",
                "--header",
                "referer: https://www.tokopedia.com/", "--data-raw", requestBody).start();
        try (InputStream inputStream = curl.getInputStream()) {
            return new String(ByteStreams.toByteArray(inputStream));
        } finally {
            curl.destroy();
        }
    }

    private String generateSuggestionRequest(String term) {
        return String.format("[{\"operationName\":\"SearchModalQuery\",\"variables\":{\"lang\":\"id\",\"device\":\"de" +
                        "sktop\",\"navsource\":\"\",\"safeSearch\":\"true\",\"source\":\"search\",\"q\":\"%s\",\"" +
                        "uniqueId\":\"%s\",\"userId\":0},\"query\":\"query SearchModalQ" +
                        "uery($q: String, $uniqueId: String, $source: String, $device: String, $userId: Int, $safeSea" +
                        "rch: String, $navsource: String) {\\n  universe_search(q: $q, uniqueId: $uniqueId, source: $" +
                        "source, device: $device, userId: $userId, safeSearch: $safeSearch, navsource: $navsource) {" +
                        "\\n    data {\\n      id\\n      name\\n      items {\\n        id\\n        location\\n    " +
                        "    applink\\n        imageUrl: imageURI\\n        url\\n        keyword\\n        recom\\n " +
                        "       sc\\n        iskol\\n        isOfficial\\n        postCount: post_count\\n        aff" +
                        "iliateUsername: affiliate_username\\n        __typename\\n      }\\n      __typename\\n    }" +
                        "\\n    __typename\\n  }\\n}\\n\"}]",
                term, Hashing.goodFastHash(128).hashString(term, Charset.defaultCharset()));
    }

    private List<String> extractAutocomplete(String result) throws JsonProcessingException {
        log.trace("extractAutocomplete: {}", result);
        List<String> suggestion = Lists.newLinkedList();
        for (JsonNode node : objectMapper.readTree(result)) {
            for (JsonNode data : node.get("data").get("universe_search").get("data")) {
                if (data.get("id").asText().equals("autocomplete")) {
                    for (JsonNode items : data.get("items")) {
                        suggestion.add(items.get("keyword").asText());
                    }
                }
            }
        }
        return suggestion;
    }

    @Override
    public Collection<ShopResult> getShopSearchResult(String query) throws IOException, InterruptedException {
        return Sets.union(searchStore(query), searchByProduct(query));
    }

    @Override
    public Collection<ProductResult> getProductSearchResult(long store, String query) throws IOException, InterruptedException {
        String body = generateSearchProductByShop(store, query);
        String response = post(body);
        return extractProductResult(response);
    }

    private Set<ShopResult> searchStore(String query) throws IOException, InterruptedException {
        String body = generateSearchStore(query);
        String response = post(body);
        return extractStore(response);

    }

    private Set<ShopResult> searchByProduct(String query) throws IOException, InterruptedException {
        String body = generateSearchProduct(query);
        String response = post(body);
        return extractStoreFromProductResult(response);
    }

    private String generateSearchStore(String query) {
        return String.format("[{\"operationName\":\"AceSearchShop\",\"variables\":{\"params\":\"q=%s&rows=100&start=0" +
                "&user_id=0\"},\"query\":\"query AceSearchShop($params: String!) {\\n  aceSearchShop(params: $params)" +
                " {\\n    totalData: total_shop\\n    shops {\\n      id: shop_id\\n      name: shop_name\\n      dom" +
                "ain: shop_domain\\n      ownerId: shop_is_owner\\n      city: shop_location\\n      shopStatus: shop" +
                "_status\\n      tagLine: shop_tag_line\\n      desc: shop_description\\n      reputationScore: reput" +
                "ation_score\\n      totalFave: shop_total_favorite\\n      isPowerBadge: shop_gold_shop\\n      isOf" +
                "ficial: is_official\\n      url: shop_url\\n      imageURL: shop_image\\n      reputationImageURL: r" +
                "eputation_image_uri\\n      shopLucky: shop_lucky\\n      products {\\n        id\\n        name\\n " +
                "       url\\n        price\\n        productImg: image_url\\n        priceText: price_format\\n     " +
                "   __typename\\n      }\\n      GAKey: ga_key\\n      favorited\\n      voucher {\\n        freeShip" +
                "ping: free_shipping\\n        cashback {\\n          cashbackValue: cashback_value\\n          isPer" +
                "centage: is_percentage\\n          __typename\\n        }\\n        __typename\\n      }\\n      __t" +
                "ypename\\n    }\\n    __typename\\n  }\\n}\\n\"}]", query);
    }

    private Set<ShopResult> extractStore(String body) throws JsonProcessingException {
        Set<ShopResult> shopResults = Sets.newHashSet();
        for (JsonNode jsonNode : objectMapper.readTree(body)) {
            for (JsonNode shop : jsonNode.get("data").get("aceSearchShop").get("shops")) {
                shopResults.add(new ShopResult(
                        ECommercePlatform.TOKOPEDIA,
                        shop.get("id").asLong(),
                        shop.get("name").asText(),
                        shop.get("url").asText()
                ));
            }
        }
        return shopResults;
    }

    private String generateSearchProduct(String query) {
        return String.format("[{\"operationName\":\"SearchProductQuery\",\"variables\":{\"params\":\"scheme=https&dev" +
                        "ice=desktop&related=true&st=product&q=%s&ob=23&page=1&variants=&shipping=&start=0&rows=200&user_id=&u" +
                        "nique_id=%s&safe_search=false&source=search\"},\"query\":\"query Searc" +
                        "hProductQuery($params: String) {\\n  searchProduct(params: $params) {\\n    source\\n    totalData: " +
                        "count\\n    totalDataText: count_text\\n    additionalParams: additional_params\\n    redirection {" +
                        "\\n      redirectionURL: redirect_url\\n      departmentID: department_id\\n      __typename\\n    " +
                        "}\\n    responseCode: response_code\\n    keywordProcess: keyword_process\\n    suggestion {\\n     " +
                        " suggestion\\n      suggestionCount\\n      currentKeyword\\n      instead\\n      insteadCount\\n  " +
                        "    suggestionText: text\\n      suggestionTextQuery: query\\n      __typename\\n    }\\n    related" +
                        " {\\n      relatedKeyword: related_keyword\\n      otherRelated: other_related {\\n        keyword" +
                        "\\n        url\\n        __typename\\n      }\\n      __typename\\n    }\\n    isQuerySafe\\n    tic" +
                        "ker {\\n      text\\n      query\\n      typeID: type_id\\n      __typename\\n    }\\n    products {" +
                        "\\n      id\\n      name\\n      childs\\n      url\\n      imageURL: image_url\\n      imageURL300:" +
                        " image_url_300\\n      imageURL500: image_url_500\\n      imageURL700: image_url_700\\n      price" +
                        "\\n      priceRange: price_range\\n      category: department_id\\n      categoryID: category_id\\n " +
                        "     categoryName: category_name\\n      categoryBreadcrumb: category_breadcrumb\\n      discountPer" +
                        "centage: discount_percentage\\n      originalPrice: original_price\\n      shop {\\n        id\\n  " +
                        "      name\\n        url\\n        isPowerBadge: is_power_badge\\n        isOfficial: is_official\\n" +
                        "        location\\n        city\\n        reputation\\n        clover\\n        __typename\\n      }" +
                        "\\n      wholesalePrice: whole_sale_price {\\n        quantityMin: quantity_min\\n        quantityMa" +
                        "x: quantity_max\\n        price\\n        __typename\\n      }\\n      courierCount: courier_count" +
                        "\\n      condition\\n      labels {\\n        title\\n        color\\n        __typename\\n      }" +
                        "\\n      labelGroups: label_groups {\\n        position\\n        type\\n        title\\n        __t" +
                        "ypename\\n      }\\n      badges {\\n        title\\n        imageURL: image_url\\n        show\\n  " +
                        "      __typename\\n      }\\n      isFeatured: is_featured\\n      rating\\n      countReview: count" +
                        "_review\\n      stock\\n      GAKey: ga_key\\n      preorder: is_preorder\\n      wishlist\\n      s" +
                        "hop {\\n        id\\n        name\\n        url\\n        goldmerchant: is_power_badge\\n        loc" +
                        "ation\\n        city\\n        reputation\\n        clover\\n        official: is_official\\n       " +
                        " __typename\\n      }\\n      __typename\\n    }\\n    " +
                        "__typename\\n  }\\n}\\n\"}]", urlFormParameterEscaper().escape(query),
                Hashing.goodFastHash(128).hashString(query, Charset.defaultCharset()));
    }

    private Set<ShopResult> extractStoreFromProductResult(String body) throws JsonProcessingException {
        Set<ShopResult> shopResults = Sets.newHashSet();
        for (JsonNode jsonNode : objectMapper.readTree(body)) {
            for (JsonNode products : jsonNode.get("data").get("searchProduct").get("products")) {
                JsonNode shop = products.get("shop");
                shopResults.add(new ShopResult(
                        ECommercePlatform.TOKOPEDIA,
                        shop.get("id").asLong(),
                        shop.get("name").asText(),
                        shop.get("url").asText()));
            }
        }
        return shopResults;
    }

    private String generateSearchProductByShop(long shopId, String query) {
        return String.format("[{\"operationName\":\"ShopProducts\",\"variables\":{\"sid\":\"%s\",\"page\":1,\"" +
                "perPage\":5,\"keyword\":\"%s\",\"etalaseId\":\"etalase\",\"sort\":1},\"query\":\"query " +
                "ShopProducts($sid: String!, $page: Int, $perPage: Int, $keyword: String, $etalaseId: String," +
                " $sort: Int) {\\n  GetShopProduct(shopID: $sid, filter: {page: $page, perPage: $perPage, fkeyword:" +
                " $keyword, fmenu: $etalaseId, sort: $sort}) {\\n    status\\n    errors\\n    links {\\n   " +
                "   prev\\n      next\\n      __typename\\n    }\\n    data {\\n      name\\n      product_url\\n" +
                "      product_id\\n      price {\\n        text_idr\\n        __typename\\n      }\\n   " +
                "   primary_image {\\n        original\\n        thumbnail\\n        resize300\\n       " +
                " __typename\\n      }\\n      flags {\\n        isSold\\n        isPreorder\\n        " +
                "isWholesale\\n        isWishlist\\n        __typename\\n      }\\n      campaign {\\n     " +
                "   discounted_percentage\\n        original_price_fmt\\n        start_date\\n       " +
                " end_date\\n        __typename\\n      }\\n      label {\\n        color_hex\\n      " +
                "  content\\n        __typename\\n      }\\n      badge {\\n        title\\n        image_url\\n " +
                "       __typename\\n      }\\n      stats {\\n        reviewCount\\n        rating\\n   " +
                "     __typename\\n      }\\n      category {\\n        id\\n        __typename\\n      }\\n   " +
                "   __typename\\n    }\\n    __typename\\n  }\\n}\\n\"}]", shopId, query);
    }

    private Set<ProductResult> extractProductResult(String body) throws JsonProcessingException {
        Set<ProductResult> productResults = Sets.newHashSet();
        for (JsonNode jsonNode : objectMapper.readTree(body)) {
            for (JsonNode item : jsonNode.get("data").get("GetShopProduct").get("data")) {
                productResults.add(new ProductResult(
                        ECommercePlatform.TOKOPEDIA,
                        item.get("product_id").asLong(),
                        item.get("name").asText(),
                        item.get("product_url").asText(),
                        item.get("price").get("text_idr").asText(),
                        item.get("primary_image").get("original").asText()
                ));
            }
        }
        return productResults;
    }

}
