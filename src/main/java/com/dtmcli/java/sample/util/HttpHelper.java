package com.dtmcli.java.sample.util;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pub.dtm.client.constant.Constants;
import pub.dtm.client.exception.FailureException;

public class HttpHelper {
	
    private static final OkHttpClient CLIENT = 
    		new OkHttpClient
    		.Builder()
    		.connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(200, 1, TimeUnit.MINUTES))
            .build();

    public static final MediaType MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    /**
     * send get request
     * @param url url
     * @return http response
     * @throws IOException exception
     */
    public static Response get(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        return CLIENT.newCall(request).execute();
    }

    /**
     * send post request
     * @param url url
     * @param json body json
     * @return http response
     * @throws IOException exception
     */
    public static Response post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(MEDIA_TYPE, json);
        Request request = new Request.Builder().url(url).post(body).build();
        return CLIENT.newCall(request).execute();
    }

    /**
     * splice url with query map
     * @param url  main url
     * @param params query map
     * @return string
     */
    public static String splicingUrl(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url).append("?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        return builder.deleteCharAt(builder.length() - 1).toString();
    }


    /**
     * splice url without query map
     * @param ip ip
     * @param port port
     * @param path path
     * @return string
     */
    public static String splicingUrl(String ip, int port, String path) {
        return Constants.HTTP_PREFIX + ip + ":" + String.valueOf(port) + path;
    }

    /**
     * check response
     * @param response http response
     * @throws Exception exception
     */
    public static void checkResult(Response response) throws Exception {
        if (response.code() >= Constants.RESP_ERR_CODE){
            throw new FailureException(response.message());
        }
        ResponseBody body = response.body();
        String result;
        if (body == null || StringUtils.isBlank(result = body.string())) {
            throw new FailureException("response is null");
        }
        if (result.contains(Constants.FAILURE_RESULT)){
            throw new FailureException("Service returned failed");
        }
    }
}
