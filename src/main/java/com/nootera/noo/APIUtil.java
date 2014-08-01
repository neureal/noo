/*
 * Copyright © 2014 BownCo
 * All rights reserved.
 */

package com.nootera.noo;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 *
 * @author Wil
 */
public class APIUtil {
	private static RequestConfig requestConfig = RequestConfig.custom().setAuthenticationEnabled(true)
				.setConnectionRequestTimeout(60000)
				.setConnectTimeout(60000).setSocketTimeout(60000)
				.setStaleConnectionCheckEnabled(true).build();
	
	public static String jsonHttpPost(String url, String request) {
		System.out.println("httpPost request: " + request);

		String reply = "";
		try (CloseableHttpClient client = HttpClients.custom()
				.setDefaultCredentialsProvider(null)
				.setDefaultRequestConfig(requestConfig)
				.disableAutomaticRetries().build();) {

			HttpPost post = new HttpPost(url);
			post.addHeader("Content-Type", "application/json-rpc");
			post.setEntity(new StringEntity(request, ContentType.create("application/json", "UTF-8")));
			
			ResponseHandler<String> handler = new ResponseHandler<String>() {
				@Override
				public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
					StatusLine status = response.getStatusLine();
					int code = status.getStatusCode();
					String phrase = status.getReasonPhrase();
					HttpEntity entity = response.getEntity();
					String results = (entity != null) ? EntityUtils.toString(entity) : "";
					if ((code != HttpStatus.SC_OK) && (code != HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
						System.out.println(code + " " + phrase);
						throw new ClientProtocolException(code + " " + phrase);
					}
					return results;
				}
			};
			
			reply = client.execute(post, handler);
		} catch (IOException ex) {
			System.out.println(ex.toString());
		}
		System.out.println("httpPost response: " + reply);	
		return reply;
	}
	
	public static JsonValue jsonRPCInvoke(String url, String method) {
		return jsonRPCInvoke(method, null);
	}
	public static JsonValue jsonRPCInvoke(String url, String method, JsonArray parameters) {
			JsonObjectBuilder builder = Json.createObjectBuilder();
			builder.add("jsonrpc", "2.0").add("method", method);
			if (parameters != null) builder.add("params", parameters);
			else builder.addNull("params");
			String guid = UUID.randomUUID().toString();
			builder.add("id", guid);
			
			JsonObject request = builder.build();
			JsonObject response = jsonObject(jsonValue(jsonHttpPost(url, String.valueOf(request))));
			
			if (response == null) {
				System.out.println("jsonRPCInvoke: json value is empty");
				return null;
			}
			if (!(guid.equals(jsonId(response)))) {
				System.out.println("jsonRPCInvoke: invalid json id");
				return null;
			}
			
			JsonValue error = response.get("error");
			if ((error != null) && (error.getValueType().equals(JsonValue.ValueType.OBJECT))) {
				JsonObject errorObj = (JsonObject)error;
				int code = errorObj.getInt("code");
				String message = errorObj.getString("message");
				JsonObject data = (JsonObject) errorObj.get("data");
				String dataStr = (data == null) ? "" : (" " + String.valueOf(data));
				System.out.println("error: " + code + " " + message + dataStr);
			}
			return response.get("result");
	}
	
	
	public static JsonValue jsonValue(String source) {
		try (JsonReader reader = Json.createReader(new StringReader(source))) {
			return reader.read();
		} catch (Throwable ex) {
			System.out.println(ex.toString());
		}
		return null;
	}
	
	public static String jsonId(String source) {
		return jsonId(jsonValue(source));
	}
	public static String jsonId(JsonValue value) {
		JsonObject o = jsonObject(value);
		if (o == null) return "";
		return o.getString("id", "");
	}
	
	public static JsonObject jsonObject(String url, String request) {
		return jsonObject(jsonValue(jsonHttpPost(url,request)));
	}
	public static JsonObject jsonObject(String source) {
		return jsonObject(jsonValue(source));
	}
	public static JsonObject jsonObject(JsonValue value) {
		if (value == null || value.getValueType() == JsonValue.ValueType.NULL) return null;
		if (value.getValueType() == JsonValue.ValueType.OBJECT && (value instanceof JsonObject)) return (JsonObject)value;
		return null;
	}
	
	public static Boolean jsonBoolean(JsonValue value) {
		if (value == null || value.getValueType() == JsonValue.ValueType.NULL) return null;
		if (value.getValueType() == JsonValue.ValueType.TRUE) return true;
		if (value.getValueType() == JsonValue.ValueType.FALSE) return false;
		return null;
	}
	
	public static String jsonString(JsonValue value) {
		if (value == null || value.getValueType() == JsonValue.ValueType.NULL) return "";
		if (value.getValueType() == JsonValue.ValueType.STRING && (value instanceof JsonString)) return ((JsonString)value).getString();
		return "";
	}
	
	public static BigDecimal jsonBigDecimal(JsonValue value) {
		if (value == null || value.getValueType() == JsonValue.ValueType.NULL) return null;
		if (value.getValueType() == JsonValue.ValueType.NUMBER && (value instanceof JsonNumber)) return ((JsonNumber)value).bigDecimalValue();
		if (value.getValueType() == JsonValue.ValueType.STRING && (value instanceof JsonString)) {
			NumberFormat f = NumberFormat.getNumberInstance();
			if (f instanceof DecimalFormat)
				((DecimalFormat)f).setParseBigDecimal(true);
			try {
				return (BigDecimal)f.parseObject(((JsonString)value).getString());
			} catch (ParseException ex) {
				System.out.println(ex.toString());
			}
		}
		return null;
	}
	public static BigInteger jsonBigInteger(JsonValue value) {
		if (value == null || value.getValueType() == JsonValue.ValueType.NULL) return null;
		if (value.getValueType() == JsonValue.ValueType.NUMBER && (value instanceof JsonNumber)) return ((JsonNumber)value).bigIntegerValue();
		if (value.getValueType() == JsonValue.ValueType.STRING && (value instanceof JsonString)) {
			try {
				return new BigInteger(((JsonString)value).getString());
			} catch (NumberFormatException ex) {
				System.out.println(ex.toString());
			}
		}
		return null;
	}
}
