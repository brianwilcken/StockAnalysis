package nlp;

import common.Tools;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NamedEntityRecognizer {

    final static Logger logger = LogManager.getLogger(NamedEntityRecognizer.class);

    public static void main(String[] args) {
        SolrClient client = new SolrClient("http://localhost:8983/solr");
        NamedEntityRecognizer namedEntityRecognizer = new NamedEntityRecognizer(client);

        //namedEntityRecognizer.trainNERModel();

        autoAnnotateAllForSymbol(client, namedEntityRecognizer, "AMRN");
    }

    private static void autoAnnotateAllForSymbol(SolrClient client, NamedEntityRecognizer namedEntityRecognizer, String symbol) {
        try {
            SolrDocumentList docs = client.QuerySolrDocuments("symbol:" + symbol + " AND body:*", 1000, 0, null);
            for (SolrDocument doc : docs) {
                String document = namedEntityRecognizer.deepCleanText((String)doc.get("body"));
                List<NamedEntity> entities = namedEntityRecognizer.detectNamedEntities(document, 0.5);
                String annotated = namedEntityRecognizer.autoAnnotate(document, entities);
                if (doc.containsKey("annotated")) {
                    doc.replace("annotated", annotated);
                } else {
                    doc.addField("annotated", annotated);
                }
                //FileUtils.writeStringToFile(new File("data/annotated.txt"), annotated, Charset.forName("Cp1252").displayName());

                //client.indexDocument(doc);
            }
        } catch (SolrServerException e) {
            e.printStackTrace();
        }
    }

    public String autoAnnotate(String document, List<NamedEntity> entities) {
        List<CoreMap> sentencesList = NLPTools.detectSentencesStanford(document);
        String[] sentences = sentencesList.stream().map(p -> p.toString()).toArray(String[]::new);
        if (!entities.isEmpty()) {
            Map<Integer, List<NamedEntity>> lineEntities = entities.stream()
                    .collect(Collectors.groupingBy(p -> p.getLine()));

            for (int s = 0; s < sentences.length; s++) {
                String sentence = sentences[s];
                if (lineEntities.containsKey(s)) {
                    List<CoreLabel> tokens = NLPTools.detectTokensStanford(sentence);
                    String[] tokensArr = tokens.stream().map(p -> p.toString()).toArray(String[]::new);
                    for (NamedEntity namedEntity : lineEntities.get(s)) {
                        namedEntity.autoAnnotate(tokensArr);
                    }
                    sentence = String.join(" ", tokensArr);
                    sentences[s] = sentence;
                }
            }
            document = String.join("\r\n", sentences);
            document = document.replaceAll(" {2,}", " "); //ensure there are no multi-spaces that could disrupt model training
            //remove random spaces that are an artifact of the tokenization process
            document = document.replaceAll("(\\b (?=,)|(?<=\\.) (?=,)|\\b (?=\\.)|(?<=,) (?=\\.)|\\b (?='))", "");
        } else {
            document = String.join("\r\n", sentences);
        }
        return document;
    }

    public List<NamedEntity> extractNamedEntities(String annotated) {
        Pattern docPattern = Pattern.compile(" ?<START:.+?<END>");
        Pattern entityTypePattern = Pattern.compile("(?<=:).+?(?=>)");

        List<CoreMap> sentencesList = NLPTools.detectSentencesStanford(annotated);
        String[] sentences = sentencesList.stream().map(p -> p.toString()).toArray(String[]::new);

        List<NamedEntity> entities = new ArrayList<>();
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            List<CoreLabel> tokens = NLPTools.detectTokensStanford(sentence);
            Matcher sentMatcher = docPattern.matcher(sentence);
            int tagTokenNum = 0;
            while(sentMatcher.find()) {
                int annotatedStart = sentMatcher.start();
                int annotatedEnd = sentMatcher.end();
                List<CoreLabel> spanTokens = tokens.stream()
                        .filter(p -> (annotatedStart == 0 || p.beginPosition() > annotatedStart) && p.endPosition() <= annotatedEnd)
                        .collect(Collectors.toList());
                CoreLabel startToken = spanTokens.get(0);
                Matcher typeMatcher = entityTypePattern.matcher(startToken.value());
                String type = null;
                if (typeMatcher.find()) {
                    type = startToken.value().substring(typeMatcher.start(), typeMatcher.end());
                }
                List<CoreLabel> entityTokens = spanTokens.subList(1, spanTokens.size() - 1); // extract just the tokens that comprise the entity

                String[] entityTokensArr = entityTokens.stream().map(p -> p.toString()).toArray(String[]::new);
                String entity = String.join(" ", entityTokensArr);
                int tokenIndexDecrement = 1 + 2 * tagTokenNum;
                int spanStart = entityTokens.get(0).get(CoreAnnotations.TokenEndAnnotation.class).intValue() - tokenIndexDecrement - 1; //subtract two token indices for every entity to accomodate for start/end tags
                int spanEnd = entityTokens.get(entityTokens.size() - 1).get(CoreAnnotations.TokenEndAnnotation.class).intValue() - tokenIndexDecrement;
                Span span = new Span(spanStart, spanEnd, type);
                NamedEntity namedEntity = new NamedEntity(entity, span, i);
                entities.add(namedEntity);
                tagTokenNum++;
            }
        }

        return entities;
    }

    private SentenceModel sentModel;
    private SolrClient client;

    public NamedEntityRecognizer(SolrClient client) {
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        this.client = client;
    }

    public List<NamedEntity> detectNamedEntities(String document, double threshold) {
        List<CoreMap> sentences = NLPTools.detectSentencesStanford(document);
        return detectNamedEntities(sentences, threshold);
    }

    public List<NamedEntity> detectNamedEntities(List<CoreMap> sentences, double threshold, int... numTries) {
        List<NamedEntity> namedEntities = new ArrayList<>();
        try {
            String modelFile = Tools.getProperty("nlp.stocksNerModel");
            TokenNameFinderModel model = NLPTools.getModel(TokenNameFinderModel.class, modelFile);
            NameFinderME nameFinder = new NameFinderME(model);

            for (int s = 0; s < sentences.size(); s++) {
                String sentence = sentences.get(s).toString();
                List<CoreLabel> tokens = NLPTools.detectTokensStanford(sentence);
                String[] tokensArr = tokens.stream().map(p -> p.toString()).toArray(String[]::new);
                Span[] nameSpans = nameFinder.find(tokensArr);
                double[] probs = nameFinder.probs(nameSpans);
                for (int i = 0; i < nameSpans.length; i++) {
                    double prob = probs[i];
                    Span span = nameSpans[i];
                    int start = span.getStart();
                    int end = span.getEnd();
                    String[] entityParts = Arrays.copyOfRange(tokensArr, start, end);
                    String entity = String.join(" ", entityParts);
                    if (prob > threshold) {
                        NamedEntity namedEntity = new NamedEntity(entity, span, s);
                        namedEntities.add(namedEntity);
                    }
                }
            }

            return namedEntities;
        } catch (IOException e) {
            if(numTries.length == 0) {
                trainNERModel(); //model may not yet exist, but maybe there is data to train it...
                return detectNamedEntities(sentences, threshold, 1);
            } else {
                //no model training data available...
                logger.error(e.getMessage(), e);
                return namedEntities; //this collection will be empty
            }
        }
    }

    public String deepCleanText(String document) {
        String document1 = document.replace("\r\n", " ");
        String document2 = document1.replace("(", " ");
        String document3 = document2.replace(")", " ");
        String document4 = document3.replaceAll("\\P{Print}", " ");
        //String document4a = Tools.removeAllNumbers(document4);
        //document = Tools.removeSpecialCharacters(document);
        String document5 = document4.replaceAll("[\\\\%-*/:-?{-~!\"^_`\\[\\]+]", " ");
        String document6= document5.replaceAll(" +\\.", ".");
        String document7 = document6.replaceAll("\\.{2,}", ". ");
        String document8 = document7.replaceAll(" {2,}", " ");
        String document9 = NLPTools.fixDocumentWordBreaks(document8);
        String document10 = document9.replaceAll("(?<=[a-z])-\\s(?=[a-z])", "");
        String document11 = document10.replaceAll("\\b\\ss\\s\\b", "'s ");

        return document11;
    }

    public void trainNERModel() {
        try {
            String trainingFile = Tools.getProperty("nlp.stocksNerTrainingFile");
            String modelFile = Tools.getProperty("nlp.stocksNerModel");

            client.writeTrainingDataToFile(trainingFile, client::getStocksNewsNERQuery, client::formatForStocksNewsNERModelTraining,
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

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
                model.serialize(modelOut);
            }

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}