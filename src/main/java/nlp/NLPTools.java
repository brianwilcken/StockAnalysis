package nlp;

import common.SpellChecker;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class NLPTools {
    final static Logger logger = LogManager.getLogger(NLPTools.class);

    public static TrainingParameters getTrainingParameters(int iterations, int cutoff) {
        TrainingParameters mlParams = new TrainingParameters();
        mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
        mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
        mlParams.put(TrainingParameters.ITERATIONS_PARAM, iterations);
        mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff);

        return mlParams;
    }

    public static <T> T getModel(Class<T> clazz, ClassPathResource modelResource) {
        try (InputStream modelIn = modelResource.getInputStream()) {

            Constructor<?> cons = clazz.getConstructor(InputStream.class);

            T o = (T) cons.newInstance(modelIn);

            return o;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T getModel(Class<T> clazz, String modelFilePath) throws IOException {
        try (InputStream modelIn = new FileInputStream(modelFilePath)) {

            Constructor<?> cons = clazz.getConstructor(InputStream.class);

            T o = (T) cons.newInstance(modelIn);

            return o;
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                IllegalArgumentException | InvocationTargetException e) {
            logger.fatal(e.getMessage(), e);
        }
        return null;
    }

    public static ObjectStream<String> getLineStreamFromString(final String data)
    {
        ObjectStream<String> lineStream = null;
        try {
            InputStreamFactory factory = new InputStreamFactory() {
                public InputStream createInputStream() throws IOException {
                    return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
                }
            };

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static ObjectStream<String> getLineStreamFromFile(final String filePath)
    {
        ObjectStream<String> lineStream = null;
        try {
            InputStreamFactory factory = new InputStreamFactory() {
                public InputStream createInputStream() throws IOException {
                    return new FileInputStream(filePath);
                }
            };

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static ObjectStream<String> getLineStreamFromMarkableFile(final String filePath)
    {
        ObjectStream<String> lineStream = null;
        try {
            MarkableFileInputStreamFactory factory = new MarkableFileInputStreamFactory(new File(filePath));

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static String[] detectSentences(SentenceModel model, String input) {
        SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);

        String[] sentences = sentenceDetector.sentDetect(input);

        return sentences;
    }

    public static String[] detectTokens(TokenizerModel model, String input) {
        TokenizerME tokenDetector = new TokenizerME(model);

        String[] tokens = tokenDetector.tokenize(input);

        return tokens;
    }

    public static List<CoreLabel> detectTokensStanford(String input) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation processed = pipeline.process(input);
        List<CoreLabel> tokens = processed.get(CoreAnnotations.TokensAnnotation.class);
        return tokens;
    }

    public static List<CoreMap> detectSentencesStanford(String input) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation processed = pipeline.process(input);
        List<CoreMap> sentences = processed.get(CoreAnnotations.SentencesAnnotation.class);
        return sentences;
    }

    public static String normalizeText(Stemmer stemmer, String text) {
        try {
            //produce a token stream for use by the stopword filters
            StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_4_9);
            TokenStream stream = analyzer.tokenStream("", text);

            //get a handle to the filter that will remove stop words
            StopFilter stopFilter = new StopFilter(Version.LUCENE_4_9, stream, analyzer.getStopwordSet());
            stream.reset();
            StringBuilder str = new StringBuilder();
            //iterate through each token observed by the stop filter
            while(stopFilter.incrementToken()) {
                //get the next token that passes the filter
                CharTermAttribute attr = stopFilter.getAttribute(CharTermAttribute.class);
                //lemmatize the token and append it to the final output
                str.append(stemmer.stem(attr.toString()) + " ");
            }
            analyzer.close();
            stopFilter.end();
            stopFilter.close();
            stream.end();
            stream.close();
            return str.toString();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    public static String fixDocumentWordBreaks(String text) {
        List<CoreMap> sentences = detectSentencesStanford(text);

        StringBuilder fixedDocument = new StringBuilder();
        for (CoreMap sentence : sentences) {
            List<TreeMap<Integer, String>> resolvedIndices = new ArrayList<>();
            List<CoreLabel> tokens = detectTokensStanford(sentence.toString());
            for (int i = 0; i < tokens.size(); i++) {
                CoreLabel token = tokens.get(i);
                String currentWord = token.word();
                TreeMap<Integer, String> currentMap = new TreeMap<>();
                currentMap.put(i, currentWord);
                if (!SpellChecker.check(currentWord.toLowerCase()) && i <= tokens.size() - 1) {
                    if (!Pattern.compile( "[0-9]" ).matcher(currentWord).find()) {

                        TreeMap<Integer, String> correctedMap = correctSpelling(tokens, i, i, new TreeMap<>(currentMap));
                        if (correctedMap != null) { //spelling correction was successful
                            resolvedIndices.add(correctedMap);
                            i = correctedMap.lastKey();
                        } else { //failed to correct spelling
                            resolvedIndices.add(currentMap);
                        }
                    } else {
                        resolvedIndices.add(currentMap);
                    }
                } else {
                    resolvedIndices.add(currentMap);
                }
            }

            int numMerges;
            do {
                numMerges = mergeWordParts(resolvedIndices);
            } while (numMerges > 0);

            StringBuilder fixedSentence = new StringBuilder();
            for (TreeMap<Integer, String> resolvedWord : resolvedIndices) {
                fixedSentence.append(StringUtils.join(resolvedWord.values(), ""));
                fixedSentence.append(tokens.get(resolvedWord.lastKey()).after());
            }

            fixedDocument.append(fixedSentence.toString());
            fixedDocument.append(System.lineSeparator());
        }

        return fixedDocument.toString();
    }

    public static int mergeWordParts(List<TreeMap<Integer, String>> resolvedIndices) {
        int numMerges = 0;
        for (int i = 0; i < resolvedIndices.size(); i++) {
            Map<Integer, String> resolvedWord = resolvedIndices.get(i);
            for (int j = i + 1; j < resolvedIndices.size(); j++) {
                Map<Integer, String> otherWord = resolvedIndices.get(j);
                if(!Collections.disjoint(resolvedWord.keySet(), otherWord.keySet())) {
                    resolvedWord.putAll(otherWord);
                    resolvedIndices.remove(j);
                    j--;
                    numMerges++;
                }
            }
        }
        return numMerges;
    }

    public static TreeMap<Integer, String> correctSpelling(List<CoreLabel> tokens, int center, int index, TreeMap<Integer, String> spellCorrection) {
        String prevToken = index > 0 ? tokens.get(index - 1).word() : null;
        String currToken = tokens.get(index).word();
        String nextToken = index < tokens.size() - 1 ? tokens.get(index + 1).word() : null;

        spellCorrection.put(index, currToken);

        boolean leftRecurseOK = false;
        if (prevToken != null && !Pattern.compile( "[0-9]" ).matcher(prevToken).find() && !spellCorrection.containsKey(index - 1)) {
            spellCorrection.put(index - 1, prevToken);
            leftRecurseOK = true;
        }

        boolean rightRecurseOK = false;
        if (nextToken != null && !Pattern.compile( "[0-9]" ).matcher(nextToken).find() && !spellCorrection.containsKey(index + 1)) {
            spellCorrection.put(index + 1, nextToken);
            rightRecurseOK = true;
        }

        TreeMap<Integer, String> left = new TreeMap(spellCorrection.headMap(center, true));
        TreeMap<Integer, String> right = new TreeMap(spellCorrection.tailMap(center, true));

        String correctedLeft = StringUtils.join(left.values(), "");
        String correctedRight = StringUtils.join(right.values(), "");
        String correctedAll = StringUtils.join(spellCorrection.values(), "");

        boolean leftPassed = SpellChecker.check(correctedLeft.toLowerCase());
        boolean rightPassed = SpellChecker.check(correctedRight.toLowerCase());
        boolean allPassed = SpellChecker.check(correctedAll.toLowerCase());

        if (rightPassed) {
            //always prefer forward direction
            return right;
        } else if (allPassed) {
            return spellCorrection;
        } else if (leftPassed) {
            //lowest priority is given to backward search
            return left;
        } else {
            if (correctedAll.length() > 30 || spellCorrection.size() == 15) {
                return null;
            } else {
                right = rightRecurseOK ? correctSpelling(tokens, center, index + 1, new TreeMap<>(spellCorrection)) : null;
                left = leftRecurseOK ? correctSpelling(tokens, center, index - 1, new TreeMap<>(spellCorrection)) : null;
                if (right != null) {
                    return right;
                } else {
                    return left;
                }
            }
        }
    }

}
