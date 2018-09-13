package solrapi.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Hex;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class IndexedStock extends IndexedObject {
	@Field
	private String id;
	@Field
	private String symbol;
	@Field
	private String interval;
	@Field
	private String timestamp;
	@Field
	private double open;
	@Field
	private double high;
	@Field
	private double low;
	@Field
	private double close;
	@Field
	private long volume;

	private static ObjectMapper mapper = new ObjectMapper();

	public void initId() {
		try {
			id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(symbol + interval + timestamp)));
		} catch (JsonProcessingException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void consume(String symbol, String interval, Map.Entry<String, Object> stockData) {
		this.symbol = symbol;
		this.interval = interval;
		timestamp = stockData.getKey();

		LinkedHashMap<String, String> priceData = (LinkedHashMap<String, String>)stockData.getValue();
		open = Double.parseDouble(priceData.get("1. open"));
		high = Double.parseDouble(priceData.get("2. high"));
		low = Double.parseDouble(priceData.get("3. low"));
		close = Double.parseDouble(priceData.get("4. close"));
		volume = Long.parseLong(priceData.get("5. volume"));
	}

	public IndexedStock() {}

	public IndexedStock(SolrDocument doc) {
		ConsumeSolr(doc);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getInterval() {
		return interval;
	}

	public void setInterval(String interval) {
		this.interval = interval;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public double getOpen() {
		return open;
	}

	public void setOpen(double open) {
		this.open = open;
	}

	public double getHigh() {
		return high;
	}

	public void setHigh(double high) {
		this.high = high;
	}

	public double getLow() {
		return low;
	}

	public void setLow(double low) {
		this.low = low;
	}

	public double getClose() {
		return close;
	}

	public void setClose(double close) {
		this.close = close;
	}

	public long getVolume() {
		return volume;
	}

	public void setVolume(long volume) {
		this.volume = volume;
	}
}
