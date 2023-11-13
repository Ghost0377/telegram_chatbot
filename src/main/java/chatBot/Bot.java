package chatBot;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Random;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.util.model.ModelUtil;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class Bot extends TelegramLongPollingBot {
    private String token;
    private String botName;
    private Map<String, String> responseMap;
    private String prompt;

    public Bot(String token, String botName) {
        this.token = token;
        this.botName = botName;

        File file = new File(this.getClass().getClassLoader().getResource("responses.json").getFile());

        try {
            String content = FileUtils.readFileToString(file, "UTF-8");
            Gson gson = new Gson();

            Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> responsesMap = gson.fromJson(content, type);
            this.responseMap = responsesMap;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public String getBotUsername() {return this.botName;}
    @Override
    public String getBotToken() {return this.token;}

    @Override
    public void onUpdateReceived(Update update) {
        var msg = update.getMessage();
        var user = msg.getFrom();
        var id = user.getId();
        prompt = msg.getText().toLowerCase();
       String defaultTemplate = this.responseMap.get("default");
       String menu = this.responseMap.get("default");
        //rule-based response
        if (this.responseMap.keySet().contains(prompt)) {
            String responseTemplate = this.responseMap.get(prompt);
            String filled_template = StringUtils.replaceEach(responseTemplate, new String[]{"__FIRSTNAME__", "__MENU__"}, new String[]{user.getFirstName(), responseMap.get("menu")});
            sendText(id, filled_template);
        //AI response
        } else {
            String category = "";
            try {
                category = findCategory(prompt);
            }
            catch(Exception e){
            }
            String response = respond(category);
            sendText(id, response);

//            var filled_template = StringUtils.replaceEach(defaultTemplate, new String[]{"__FIRSTNAME__", "__MENU__"}, new String[]{user.getFirstName(), responseMap.get("menu")});
//            sendText(id, filled_template);
        }
    }

    public void sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString()) //Who are we sending a message to
                .text(what).build();    //Message content
        try {
            execute(sm);                        //Actually sending the message
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);      //Any error will be printed here
        }
    }

    //AI implementation
    //segment an input character sequence into tokens
    public String[] tokenize(String prompt)  throws Exception {
        InputStream modelIn = new FileInputStream("en-token.bin");
        TokenizerModel model = new TokenizerModel(modelIn);
        TokenizerME tokenizer = new TokenizerME(model);
        String[] tokens = tokenizer.tokenize(prompt);
        return tokens;
    }

    //The Part of Speech Tagger marks tokens with their corresponding word type based on the token itself and the context of the token
    public String[] posTag(String[] tokens)  throws Exception{
        InputStream modelIn = new FileInputStream("en-pos-maxent.bin");
        POSModel model = new POSModel(modelIn);
        POSTaggerME tagger = new POSTaggerME(model);
        String[] tags = tagger.tag(tokens);
        return tags;
    }
    //returns, for a given word form (token) and Part of Speech tag, the dictionary form of a word, which is usually referred to as its lemma
    public String[] lemmatize(String[] tokens, String[] posTags)  throws Exception{
        InputStream modelIn = new FileInputStream("en-lemmatizer.bin");
        LemmatizerModel model = new LemmatizerModel(modelIn);
        LemmatizerME lemmatizer = new LemmatizerME(model);
        String[] lemmas = lemmatizer.lemmatize(tokens, posTags);
        return lemmas;
    }

    //classify text into pre-defined categories
    public DoccatModel trainCategorizerModel() throws FileNotFoundException, IOException {
        InputStreamFactory inputStreamFactory = new MarkableFileInputStreamFactory(new File("categories.txt"));
        ObjectStream<String> lineStream = new PlainTextByLineStream(inputStreamFactory, StandardCharsets.UTF_8);
        ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

        DoccatFactory factory = new DoccatFactory(new FeatureGenerator[] { new BagOfWordsFeatureGenerator() });
        TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
        params.put(TrainingParameters.CUTOFF_PARAM, 0);

        DoccatModel model = DocumentCategorizerME.train("en",sampleStream,params, factory);
        return model;
    }
    public String categorize(DoccatModel model, String[] tokens)  throws Exception{
        DocumentCategorizerME myCategorizer = new DocumentCategorizerME(model);

        double[] probabilitiesOfOutcomes = myCategorizer.categorize(tokens);
        String category = myCategorizer.getBestCategory(probabilitiesOfOutcomes);
        return category;
    }
    public String findCategory(String prompt) throws Exception {
        String[] tokens = tokenize(prompt);
        String[] posTags = posTag(tokens);
        String[] lemmas = lemmatize(tokens, posTags);
        DoccatModel model = trainCategorizerModel();
        String category = categorize(model, lemmas);
        return category;
    }
    public String respond(String category){

        String[] greeting = {"hi, how can I help you","Hey there, what would you like to know","hello, how may I assist you","how can I help you?"};
        if (category.equals("greeting")) {
            return greeting[(int) (Math.random() * greeting.length)];
        }
        else if (category.equals("address")) {
            return "Johannesburg(RSA):\nhttps://goo.gl/maps/HPYe79QZbMHQq2os5\nCape Town(RSA):\nhttps://goo.gl/maps/GeD9pAjGC1FRigqo7\nAmsterdam(NL):\nhttps://goo.gl/maps/bskDii3ruwXxZzHQ9";
        }
        else if (category.equals("software")) {
            return "Here are some popular software:\n\n/miro -  collaboration whiteboard\n/microsoftTeams - platform for communication and collaboration\n/bitbucket(git) - source control to manage code\n/sourcetree - git visualisation tool\n/vsCode - source code editor\n/intelliJ - source code editor\n\nExtras:\n/dependencyDiagram - shows flow of your project\n/designDoc - collection of documents and resources for your project";
        }
        else if (category.equals("contacts")) {
            return   "📞 +27 87 654 3300 (RSA)\n📞 +31 6 5936 1315 (NL)\n\n✉️ info@synthesis.co.za\n\nIT support:\nhttps://forms.office.com/Pages/ResponsePage.aspx?id=hdvPlCM9SUigZlza2WXM2I8dYGsd2KpPh0py6Nii3apUMVBZMEVVTENTUlVVNU5VTzc4S0xQVkZYRi4u\n\nLinkedIn:\nhttps://www.linkedin.com/company/synthesis-software-technologies-pty-ltd/\nTwitter:\nhttps://twitter.com/SynthesisSA\nFacebook:\nhttps://www.facebook.com/synthesis.software\nInstagram:\nhttps://www.instagram.com/synthesis_software_technology/\nYoutube:\nhttps://www.youtube.com/channel/UCeWw7klq-2rOABze8zXWOpg";
        }
        else if (category.equals("bursary")) {
            return "You can follow this link for bursary application:\nhttps://synthesissoftware.sharepoint.com/sites/Pocketguide2/SitePages/Bursary-Request-Form.asp";
        }
        else if (category.equals("leave")) {
            return "You can follow this link for leave application:\nhttps://online.sage.co.za/U59546/#/signin";
        }
        else if (category.equals("mediacenter")) {
            return "News:\nhttps://www.synthesis.co.za/news\nArticles:\nhttps://www.synthesis.co.za/articles/\nPodcast:\nhttps://www.synthesis.co.za/podcasts/";
        }
        else if (category.equals("updatedetails")) {
            return "You can update your personal details here:\nhttps://eur.delve.office.com/?u=79192890-bd29-43d2-8002-710b8757f1b4&v=work";
        }
        else if (category.equals("employeeshout")) {
            return "You can send a shoutout to your colleagues here:\nhttps://forms.office.com/Pages/ResponsePage.aspx?id=hdvPlCM9SUigZlza2WXM2I8dYGsd2KpPh0py6Nii3apUREUyUkg3MEs3UzE2Tks3UzlVRkNDRE82US4u";
        }
        else if (category.equals("conversation-continue")) {
            return "What more would you like to know";
        }
        else if (category.equals("conversation-complete")) {
            return "Goodbye, hope to chat with you soon";
        }
        else if (category.equals("comparing")) {
            return "I am a chatbot created by Rector, trained to generate human-like text to respond to whatever you may want to know about Synthesis.You can't compare me to other software as I'm trained to answer Synthesis related questions";
        }
        else if (category.equals("myself")) {
            return "I am a chatbot created by Rector, trained to generate human-like text to respond to whatever you may want to know about Synthesis. I am simply your mentor😇";
        }
        return "I don't understand your prompt🤦‍♂️.\nHere is what I can help you with:\n\n/website  /office  /Contacts  /mediaCenter  /software  /employeeShoutOut  /leave  /bursary  /updateDetails";
    }

}

