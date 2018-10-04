package nlp;

import common.Tools;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NamedEntityRecognizer {

    final static Logger logger = LogManager.getLogger(NamedEntityRecognizer.class);

    public static void main(String[] args) {
        SolrClient client = new SolrClient("http://localhost:8983/solr");
        NamedEntityRecognizer namedEntityRecognizer = new NamedEntityRecognizer(client);

        autoAnnotateAllForSymbol(client, namedEntityRecognizer, "AMRN", "SYM");

//        try {
//            namedEntityRecognizer.trainNERModel("Electricity");
//        } catch (InsufficientTrainingDataException e) {
//            e.printStackTrace();
//        }
    }

    private static void autoAnnotateAllForSymbol(SolrClient client, NamedEntityRecognizer namedEntityRecognizer, String symbol, String entityType) {
        try {
            SolrDocumentList docs = client.QuerySolrDocuments("symbol:" + symbol + " AND body:*", 1000, 0, null);
            for (SolrDocument doc : docs) {
                String document = (String)doc.get("body");
                String annotated = namedEntityRecognizer.autoAnnotate(document, entityType, 0.5);
                String annotatedKey = "annotated_" + entityType;
                if (doc.containsKey(annotatedKey)) {
                    doc.replace(annotatedKey, annotated);
                } else {
                    doc.addField(annotatedKey, annotated);
                }
                //FileUtils.writeStringToFile(new File("data/annotated.txt"), annotated, Charset.forName("Cp1252").displayName());

                client.indexDocument(doc);
            }
        } catch (SolrServerException e) {
            e.printStackTrace();
        }
    }

    public String prepForAnnotation(String document) {
        String[] sentences = detectSentences(document);
        document = String.join("\r\n", sentences);

        return document;
    }

    public String autoAnnotate(String document, String entityType, double threshold) {
        String[] sentences = detectSentences(document);
        document = String.join("\r\n", sentences);
        Map<String, Double> entities = detectNamedEntities(sentences, entityType, threshold);
        if (!entities.isEmpty()) {
            entities = entities.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            for (String tag : entities.keySet()) {
                document = document.replace(tag, " <START:" + entityType + "> " + tag + " <END> ");
            }
            document = document.replaceAll(" {2,}", " "); //ensure there are no multi-spaces that could disrupt model training
        }
        return document;
    }

    private static final Map<String, String> models;
    static
    {
        models = new HashMap<>();
        models.put("SYM", Tools.getProperty("nlp.symbolNerModel"));
    }

    private static final Map<String, String> trainingFiles;
    static
    {
        trainingFiles = new HashMap<>();
        trainingFiles.put("SYM", Tools.getProperty("nlp.symbolNerTrainingFile"));
    }

    private static final Map<String, Function<SolrQuery, SolrQuery>> dataGetters;
    static
    {
        dataGetters = new HashMap<>();
        dataGetters.put("SYM", SolrClient::getNewsNERSymbolQuery);
    }

    private SentenceModel sentModel;
    private TokenizerModel tokenizerModel;
    private SolrClient client;

    public NamedEntityRecognizer(SolrClient client) {
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));
        this.client = client;
    }

    public Map<String, Double> detectNamedEntities(String document, String entityType, double threshold) {
        String[] sentences = detectSentences(document);
        return detectNamedEntities(sentences, entityType, threshold);
    }

    public Map<String, Double> detectNamedEntities(String[] sentences, String entityType, double threshold, int... numTries) {
        Map<String, Double> namedEntities = new HashMap<>();
        try {
            TokenNameFinderModel model = NLPTools.getModel(TokenNameFinderModel.class, models.get(entityType));
            NameFinderME nameFinder = new NameFinderME(model);

            List<String> tokenized = new ArrayList<>();
            for (String sentence : sentences) {
                String[] tokens = NLPTools.detectTokens(tokenizerModel, sentence);
                tokenized.add(String.join(" ", tokens));
                Span[] nameSpans = nameFinder.find(tokens);
                double[] probs = nameFinder.probs(nameSpans);
                for (int i = 0; i < nameSpans.length; i++) {
                    double prob = probs[i];
                    Span span = nameSpans[i];
                    int start = span.getStart();
                    int end = span.getEnd();
                    String[] entityParts = Arrays.copyOfRange(tokens, start, end);
                    String entity = String.join(" ", entityParts);
                    if (!namedEntities.containsKey(entity) && prob > threshold) {
                        namedEntities.put(entity, prob);
                    }
                }
            }

//        try {
//            FileUtils.writeLines(new File("data/sentences.txt"), Charset.forName("Cp1252").displayName(), Arrays.asList(sentences));
//            FileUtils.writeLines(new File("data/tokenized.txt"), Charset.forName("Cp1252").displayName(), tokenized);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

            return namedEntities;
        } catch (IOException e) {
            if(numTries.length == 0) {
                trainNERModel(entityType); //model may not yet exist, but maybe there is data to train it...
                return detectNamedEntities(sentences, entityType, threshold, 1);
            } else {
                //no model training data available...
                logger.error(e.getMessage(), e);
                return namedEntities; //this collection will be empty
            }
        }
    }

    public String[] detectSentences(String document) {
        document = document.replace("\r\n", "");
        document = document.replace("(", " ");
        document = document.replace(")", " ");
        document = document.replaceAll("\\P{Print}", " ");
        //document = document.replaceAll("(\\w+\\W+)?\\d+(\\w+\\W+)?", ""); //removes all the numbers
        //document = document.replaceAll("[$-,/:-?{-~!\"^_`\\[\\]+]", ""); //removes most special characters
        document = document.replaceAll("[%-*/:-?{-~!\"^_`\\[\\]+]", "");
        //document = document.replaceAll("-", " ");
        document = document.replaceAll(" +\\.", ".");
        document = document.replaceAll("\\.{2,}", ". ");
        document = document.replaceAll(" {2,}", " ");
        String[] sentences = NLPTools.detectSentences(sentModel, document);

        return sentences;
    }

    public void trainNERModel(String entityType) {
        try {
            String trainingFile = trainingFiles.get(entityType);

            client.writeTrainingDataToFile(trainingFile, dataGetters.get(entityType), client::formatForNERSymbolTraining,
                    new SolrClient.ClusteringThrottle("", 0));
            ObjectStream<String> lineStream = NLPTools.getLineStreamFromMarkableFile(trainingFile);

            TokenNameFinderModel model;

            TrainingParameters params = new TrainingParameters();
            params.put(TrainingParameters.ITERATIONS_PARAM, 300);
            params.put(TrainingParameters.CUTOFF_PARAM, 1);

            try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
                model = NameFinderME.train("en", null, sampleStream, params,
                        TokenNameFinderFactory.create(null, null, Collections.emptyMap(), new BioCodec()));
            }

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(models.get(entityType)))) {
                model.serialize(modelOut);
            }

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}