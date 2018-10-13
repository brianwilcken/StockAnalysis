package solrapi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

import common.Tools;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.util.SimpleOrderedMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Files;
import solrapi.model.IndexedNews;
import solrapi.model.IndexedStock;

public class SolrClient {

    private final static String COLLECTION = "stocks";

    final static Logger logger = LogManager.getLogger(SolrClient.class);

    private HttpSolrClient client;
    private static ObjectMapper mapper = new ObjectMapper();

    public SolrClient(String solrHostURL) {
        client = new HttpSolrClient.Builder(solrHostURL).build();
    }

    public static void main(String[] args) {
        SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
        //solrClient.writeTrainingDataToFile("data/news-clustering.csv", solrClient::getNewsClusteringQuery, solrClient::formatForClustering, new ClusteringThrottle("", 0));

        SortClause sort = new SortClause("random_*", SolrQuery.ORDER.asc);
        try {
            List<IndexedNews> nerNews = solrClient.QueryIndexedDocuments(IndexedNews.class, "body:*", 100, 0, sort);
            List<String> articles = nerNews.stream().map(p -> p.GetNERModelTrainingForm()).collect(Collectors.toList());
            FileWriter writer = new FileWriter("data/ner-symbol.train");
            for(String str: articles) {
                writer.write(str);
            }
            writer.close();
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }

    public void ImportStocksFromJsonFile(String filePath) throws SolrServerException {
        String file = Tools.GetFileString(filePath, "Cp1252");
        try {
            IndexedStock[] stocks = mapper.readValue(file, IndexedStock[].class);
            for (IndexedStock stock : stocks) {
                stock.initId();
            }
            indexDocuments(Arrays.asList(stocks));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void retrieveAnnotatedData(SolrClient client, String id) {
        try {
            SolrDocumentList docs = client.QuerySolrDocuments("id:" + id, 1, 0, null);
            SolrDocument doc = docs.get(0);

            String annotated = (String)doc.get("annotated");

            FileUtils.writeStringToFile(new File("data/annotated.txt"), annotated, Charset.forName("Cp1252").displayName());
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void updateAnnotatedData(SolrClient client, String id) {
        String annotated = Tools.GetFileString("data/annotated.txt");
        try {
            SolrDocumentList docs = client.QuerySolrDocuments("id:" + id, 1, 0, null);
            SolrDocument doc = docs.get(0);
            if (doc.containsKey("annotated")) {
                doc.replace("annotated", annotated);
            } else {
                doc.addField("annotated", annotated);
            }

            client.indexDocument(doc);
        } catch (SolrServerException e) {
            e.printStackTrace();
        }
    }

    //	public List<AnalyzedNews> CollectAnalyzedEventsFromCSV(String filePath) {
//		String file = Tools.GetFileString(filePath, "Cp1252");
//		try {
//			MappingIterator<AnalyzedNews> eventIter = new CsvMapper().readerWithTypedSchemaFor(AnalyzedNews.class).readValues(file);
//			List<AnalyzedNews> events = eventIter.readAll();
//			List<AnalyzedNews> analyzedEvents = events.stream()
//					.filter(p -> !Strings.isNullOrEmpty(p.category))
//					.collect(Collectors.toList());
//
//			return analyzedEvents;
//		} catch (IOException e) {
//			return null;
//		}
//	}
//
//	public List<IndexedNews> CollectIndexedEventsFromAnalyzedEvents(List<AnalyzedNews> analyzedEvents) throws SolrServerException {
//		List<String> ids = analyzedEvents.stream()
//				.map(p -> p.id)
//				.collect(Collectors.toList());
//
//		List<IndexedNews> indexedEvents = new ArrayList<>();
//
//		for (int i = 0; i < ids.size(); i++) {
//			String id = ids.get(i);
//			indexedEvents.addAll(QueryIndexedDocuments(IndexedNews.class, "id:" + id, 1, 0, null));
//		}
//
//		return indexedEvents;
//	}
//
//	public void UpdateIndexedEventsWithAnalyzedEvents(String filePath) throws SolrServerException {
//		List<AnalyzedNews> analyzedEvents = CollectAnalyzedEventsFromCSV(filePath);
//		List<IndexedNews> indexedEvents = CollectIndexedEventsFromAnalyzedEvents(analyzedEvents);
//
//		List<IndexedNews> indexReadyEvents = new ArrayList<>();
//
//		int totalDocs = analyzedEvents.size();
//		logger.info("Total documents to process: " + totalDocs);
//		for (int i = 0; i < totalDocs; i++) {
//			AnalyzedNews analyzedEvent = analyzedEvents.get(i);
//			Optional<IndexedNews> maybeEvent = indexedEvents.stream().filter(p -> p.getId().compareTo(analyzedEvent.id) == 0).findFirst();
//			if (maybeEvent.isPresent()) {
//				IndexedNews indexedEvent = maybeEvent.get();
//				indexedEvent.setCategory(analyzedEvent.category);
//				indexedEvent.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_USER_UPDATED);
//				indexedEvent.setEventState(SolrConstants.Events.EVENT_STATE_ANALYZED);
//				indexedEvent.updateLastUpdatedDate();
//
//				indexReadyEvents.add(indexedEvent);
//			}
//			logger.info("Processed document: #" + (i + 1) + " of " + totalDocs);
//		}
//
//		indexDocuments(indexReadyEvents);
//	}

    public <T> void indexDocuments(Collection<T> docs) throws SolrServerException {
        try {
            if (!docs.isEmpty()) {
                client.addBeans(COLLECTION, docs);
                UpdateResponse updateResponse = client.commit(COLLECTION);

                if (updateResponse.getStatus() != 0) {
                    //TODO What should happen if the update fails?
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public <T> void indexDocument(T doc) throws SolrServerException {
        if (doc != null) {
            List<T> docs = new ArrayList<>();
            docs.add(doc);
            indexDocuments(docs);
        }
    }

    public void deleteDocuments(String query) throws SolrServerException {
        try {
            client.deleteByQuery(COLLECTION, query);
            UpdateResponse updateResponse = client.commit(COLLECTION);

            if (updateResponse.getStatus() != 0) {
                //TODO What should happen if the update fails?
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public Boolean DocumentExists(String queryStr) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setRows(0);
        query.setQuery(queryStr);
        try {
            QueryResponse response = client.query(COLLECTION, query);
            return response.getResults().getNumFound() > 0;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    public SimpleOrderedMap<?> QueryFacets(String queryStr, String facetQuery) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setRows(0);
        query.setQuery(queryStr);
        query.add("json.facet", facetQuery);
        try {
            QueryResponse response = client.query(COLLECTION, query);
            SimpleOrderedMap<?> facets = (SimpleOrderedMap<?>) response.getResponse().get("facets");
            return facets;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public SolrDocumentList FindSimilarDocuments(String searchText) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setRequestHandler("/" + MoreLikeThisParams.MLT);
        query.setParam(CommonParams.STREAM_BODY, searchText);
        query.setRows(20);
        try {
            SolrDocumentList response = client.query(COLLECTION, query).getResults();
            return response;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public SolrDocumentList QuerySolrDocuments(String queryStr, int rows, int start, SortClause sort, String... filterQueries) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setQuery(queryStr);
        if (filterQueries != null) {
            query.setFilterQueries(filterQueries);
        }
        query.setRows(rows);
        query.setStart(start);
        if (sort != null) {
            query.setSort(sort);
        } else {

        }
        try {
            SolrDocumentList response = client.query(COLLECTION, query).getResults();
            return response;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public <T> List<T> QueryIndexedDocuments(Class<T> clazz, String queryStr, int rows, int start, SortClause sort, String... filterQueries) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setQuery(queryStr);
        if (filterQueries != null) {
            query.setFilterQueries(filterQueries);
        }
        query.setRows(rows);
        query.setStart(start);
        if (sort != null) {
            query.setSort(sort);
        } else {

        }
        try {
            Constructor<?> cons;
            try {
                cons = clazz.getConstructor(SolrDocument.class);
            } catch (NoSuchMethodException | SecurityException e1) {
                return null;
            }
            SolrDocumentList response = client.query(COLLECTION, query).getResults();
            List<T> typedDocs = convertSolrDocsToTypedDocs(cons, response);
            return typedDocs;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private <T> List<T> convertSolrDocsToTypedDocs(Constructor<?> cons, SolrDocumentList docs) {
        List<T> typedDocs = (List<T>) docs.stream().map(p -> {
            try {
                return cons.newInstance(p);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                logger.error(e.getMessage(), e);
                return null;
            }
        }).collect(Collectors.toList());

        return typedDocs;
    }

    public SolrQuery getNewsClusteringQuery(SolrQuery query) {
        query.setQuery("body:*");
        query.setRows(10000000);

        return query;
    }

    public static SolrQuery getNewsNERSymbolQuery(SolrQuery query) {
        query.setQuery("annotated:*");
        query.setRows(1000000);

        return query;
    }

    public void formatForCategorization(IndexedNews news, FileOutputStream fos) throws IOException {
        fos.write(news.GetDoccatModelTrainingForm().getBytes(Charset.forName("Cp1252")));
    }

    public void formatForClustering(IndexedNews news, FileOutputStream fos) throws IOException {
        fos.write(news.GetClusteringForm().getBytes(Charset.forName("Cp1252")));
    }

    public void formatForAnalysis(IndexedNews news, FileOutputStream fos) throws IOException {
        fos.write(news.GetAnalysisForm().getBytes(Charset.forName("Cp1252")));
    }

    public void formatForNERSymbolTraining(IndexedNews news, FileOutputStream fos) throws IOException {
        fos.write(news.GetNERModelTrainingForm().getBytes(Charset.forName("Cp1252")));
    }

    public void formatForNERModelTraining(SolrDocument doc, FileOutputStream fos) throws IOException {
        String[] lines = ((String)doc.get("annotated")).split("\r\n");
        List<String> annotatedLines = Arrays.stream(lines).filter(p -> p.contains("<START:")).collect(Collectors.toList());
        String onlyAnnotated = String.join("\r\n", annotatedLines);
        fos.write(onlyAnnotated.getBytes(Charset.forName("Cp1252")));
    }

    public void writeTrainingDataToFile(String trainingFilePath, Function<SolrQuery, SolrQuery> queryGetter,
                                        Tools.CheckedBiConsumer<IndexedNews, FileOutputStream> consumer, TrainingDataThrottle throttle) {
        SolrQuery query = queryGetter.apply(new SolrQuery());
        try {
            File file = new File(trainingFilePath);
            file.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            final BlockingQueue<SolrDocument> tmpQueue = new LinkedBlockingQueue<>();
            client.queryAndStreamResponse(COLLECTION, query, new CallbackHandler(tmpQueue));
            throttle.init(tmpQueue.size());

            SolrDocument tmpDoc;
            do {
                tmpDoc = tmpQueue.take();
                if (!(tmpDoc instanceof StopDoc)) {
                    IndexedNews news = new IndexedNews(tmpDoc);
                    if (throttle.check(news)) {
                        consumer.apply(news, fos);
                        fos.write(System.lineSeparator().getBytes());
                    }
                }
            } while (!(tmpDoc instanceof StopDoc));

            fos.close();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void WriteStockDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
        ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
        List<IndexedStock> events = QueryIndexedDocuments(IndexedStock.class, queryStr, rows, 0, null, filterQueries);
        try {
            String output = writer.writeValueAsString(events);
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            Files.write(output, file, Charset.forName("Cp1252"));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void WriteNewsDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
        ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
        List<IndexedNews> sources = QueryIndexedDocuments(IndexedNews.class, queryStr, rows, 0, null, filterQueries);
        try {
            String output = writer.writeValueAsString(sources);
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            Files.write(output, file, Charset.forName("Cp1252"));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private class StopDoc extends SolrDocument {
        // marker to finish queuing
    }

    private class CallbackHandler extends StreamingResponseCallback {
        private BlockingQueue<SolrDocument> queue;
        private long currentPosition;
        private long numFound;

        public CallbackHandler(BlockingQueue<SolrDocument> aQueue) {
            queue = aQueue;
        }

        @Override
        public void streamDocListInfo(long aNumFound, long aStart, Float aMaxScore) {
            // called before start of streaming
            // probably use for some statistics
            currentPosition = aStart;
            numFound = aNumFound;
            if (numFound == 0) {
                queue.add(new StopDoc());
            }
        }

        @Override
        public void streamSolrDocument(SolrDocument doc) {
            currentPosition++;
            queue.add(doc);
            if (currentPosition == numFound) {
                queue.add(new StopDoc());
            }
        }
    }

    private static abstract class TrainingDataThrottle {

        protected String throttleFor;
        protected double throttlePercent;

        public TrainingDataThrottle(String throttleFor, double throttlePercent) {
            this.throttleFor = throttleFor;
            this.throttlePercent = throttlePercent;
        }

        public abstract void init(int numDocs);

        public abstract boolean check(IndexedNews news);
    }

    public static class StockNewsCategorizationThrottle extends TrainingDataThrottle {

        private int numDocs;
        private int throttleForCount;

        public StockNewsCategorizationThrottle(String throttleFor, double throttlePercent) {
            super(throttleFor, throttlePercent);
        }

        @Override
        public void init(int numDocs) {
            this.numDocs = numDocs;
        }

        @Override
        public boolean check(IndexedNews news) {
            if (news.getCategory().compareTo(throttleFor) == 0) {
                double currentPercent = (double)throttleForCount / (double)numDocs;
                if (currentPercent > throttlePercent) {
                    return false;
                } else {
                    //randomization such that not always given document is added
                    //50% likelihood the document is added
                    double random = Math.random();
                    if (random > 0.5) {
                        throttleForCount++;
                        return true;
                    }
                    return false;
                }
            }
            return true;
        }
    }

    public static class ClusteringThrottle extends TrainingDataThrottle {

        public ClusteringThrottle(String throttleFor, double throttlePercent) {
            super(throttleFor, throttlePercent);
        }

        @Override
        public void init(int numDocs) {

        }

        @Override
        public boolean check(IndexedNews news) {
            return true;
        }
    }
}
