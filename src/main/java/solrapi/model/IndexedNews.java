package solrapi.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import common.DetectHtml;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import nlp.NLPTools;
import opennlp.tools.stemmer.Stemmer;
import org.apache.commons.codec.binary.Hex;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import common.Tools;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.TokenizerModel;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class IndexedNews extends IndexedObject  {
	@Field
	private String id;
	@Field
	private String symbol;
	@Field
	private String articleDate;
	@Field
	private String title;
	@Field
	private String body;
	@Field
	private String category;
	@Field
	private String sentiment;
	@Field
	private String url;
	@Field
	private String annotated;

	private String parsed;

	private static ObjectMapper mapper = new ObjectMapper();

	private IndexedNews() {}

	public IndexedNews(SolrDocument doc) {
		ConsumeSolr(doc);
	}

	public void initId() {
		try {
			id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(url)));
		} catch (JsonProcessingException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static IndexedNews consume(Document article, Element result, ArticleExtractor articleExtractor) {
		IndexedNews news = new IndexedNews();
		news.title = article.title();
		news.body = news.getArticleBody(article, articleExtractor);
		news.url = article.location();

		List<String> sourceTimeStamp = result.parent().parent().select(".slp").select("span").eachText();
		if (sourceTimeStamp.size() > 0) {
			String timestamp = sourceTimeStamp.get(2);
			news.articleDate = news.getFormattedDateTimeString(timestamp);
		} else {
			return null;
		}

		return news;
	}

	private String getArticleBody(Document article, ArticleExtractor articleExtractor) {
		String body = null;
		try {
			body = articleExtractor.getText(article.body().html());
			if (DetectHtml.isHtml(body)) {
				body = articleExtractor.getText(body);
			}
		} catch (BoilerpipeProcessingException e) {
			e.printStackTrace();
		}
		return body;
	}

	private String getFormattedDateTimeString(String timestamp) {
		String sourceDate = null;
		if (timestamp.contains("ago")) {
			Integer ago = extractNumericFromString(timestamp);
			if (ago != null) {
				long millis = 0;
				if (timestamp.contains("hour")) {
					millis = TimeUnit.HOURS.toMillis(ago);
				} else if (timestamp.contains("minute")) {
					millis = TimeUnit.MINUTES.toMillis(ago);
				}
				sourceDate = Tools.getFormattedDateTimeString(Instant.now().minusMillis(millis));
			}
		} else {
			String pattern = "MMM dd, yyyy";
			DateFormat df = new SimpleDateFormat(pattern);
			try {
				Date date = df.parse(timestamp);
				sourceDate = Tools.getFormattedDateTimeString(date.toInstant());
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

		return sourceDate;
	}

	private Integer extractNumericFromString(String input) {
		Pattern pattern = Pattern.compile("\\d+");
		Matcher matcher = pattern.matcher(input);
		if (matcher.find()) {
			Integer num = Integer.decode(matcher.group());
			return num;
		}
		return null;
	}

	public String[] GetDocCatTokens(TokenizerModel model, Stemmer stemmer) {
		String normalized = getNormalizedDocCatString(stemmer);
		String[] tokens = NLPTools.detectTokens(model, normalized);

		return tokens;
	}

	private String getNormalizedDocCatString(Stemmer stemmer) {
		String bodySub;
		int maxChars = 512;
		int bodyLength = body.length();
		if (bodyLength < maxChars) {
			bodySub = body;
		} else {
			bodySub = body.substring(0, maxChars - 1);
		}

		String docCatStr = symbol + " " + title + " " + bodySub;
		docCatStr = docCatStr.replace("\r", " ").replace("\n", " ");

		return NLPTools.normalizeText(stemmer, docCatStr);
	}

	public String GetDoccatModelTrainingForm() {
		return category + "\t" + getNormalizedDocCatString(new PorterStemmer());
	}

	public String GetSentimentModelTrainingForm() {
		return sentiment + "\t" + getNormalizedDocCatString(new PorterStemmer());
	}

	public String GetNERModelTrainingForm() {
		return body;
	}

	public String GetClusteringForm() {
        String clusteringStr = id + "," + symbol + "," + title.replace(",", "") + "," + body.replace(",", "");
        clusteringStr = clusteringStr.replace("\r", " ")
                .replace("\n", " ");

        return clusteringStr;
	}

	public String GetAnalysisForm() {
		String analysisStr = "0," + id + "," + title.replace(",", "").replace("\"", "'") + ",cluster," + "0," + category;
		analysisStr = analysisStr.replace("\r", " ")
				.replace("\n", " ");

		return analysisStr;
	}

	public String getNERReportingForm() {
		return id;
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

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}

	public String getArticleDate() {
		return articleDate;
	}

	public void setArticleDate(String articleDate) {
		this.articleDate = articleDate;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getSentiment() {
		return sentiment;
	}

	public void setSentiment(String sentiment) {
		this.sentiment = sentiment;
	}

	public String getAnnotated() {
		return annotated;
	}

	public void setAnnotated(String annotated) {
		this.annotated = annotated;
	}

	public String getParsed() {
		return parsed;
	}

	public void setParsed(String parsed) {
		this.parsed = parsed;
	}
}
