/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

    This file is part of irccasino.

    irccasino is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    irccasino is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
*/
package irccasino;

import irccasino.blackjack.Blackjack;
import irccasino.texaspoker.TexasPoker;
import irccasino.cardgame.CardGame;
import irccasino.texastourney.TexasTourney;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;

/**
 * The default standalone bot that allows any number of games, each in their
 * own channel. It also logs all activity to log.txt.
 * @author Yizhe Shen
 */
public class CasinoBot extends PircBotX implements GameManager {
    
    protected HashMap<String,String> configMap;
    protected ArrayList<CardGame> gameList;
    protected String logFile;
    
    /**
     * Listener for CasinoBot initialization 
     */
    public static class InitListener extends ListenerAdapter<CasinoBot> {
        protected char commandChar;
        public InitListener(char commChar) {
            commandChar = commChar;
        }
        
        /**
         * Joins the channels in the config file upon successful connection.
         * @param event Connect event
         */
        @Override
        public void onConnect(ConnectEvent<CasinoBot> event){
            StringTokenizer st = new StringTokenizer(event.getBot().configMap.get("channel"), ",");
            while(st.hasMoreTokens()) {
                event.getBot().joinChannel(st.nextToken());
            }
        }
        
        /**
         * Parses channel messages for commands and parameters.
         * @param event Message event
         */
        @Override
        public void onMessage(MessageEvent<CasinoBot> event){
            String msg = event.getMessage();

            // Parse the message if it is a command
            if (msg.length() > 1 && msg.charAt(0) == commandChar && msg.charAt(1) != ' ') {
                msg = msg.substring(1);
                StringTokenizer st = new StringTokenizer(msg);
                String command = st.nextToken().toLowerCase();
                String[] params = new String[st.countTokens()];
                for (int ctr = 0; ctr < params.length; ctr++){
                    params[ctr] = st.nextToken();
                }
                processCommand(event.getBot(), event.getChannel(), event.getUser(), command, params);
            }
        }

        /**
         * Processes initialization commands for this bot.
         * @param bot the bot that caught the command
         * @param channel the originating channel of the command
         * @param user the user who gave the command
         * @param command the command
         * @param params the parameters after the command
         */
        public void processCommand(CasinoBot bot, Channel channel, User user, String command, String[] params){
            if (channel.isOp(user)){
                if (command.equals("games")) {
                    String str = "Games: ";
                    if (bot.gameList.isEmpty()) {
                        str += "No games";
                    } else {
                        for (CardGame game : bot.gameList) {
                            str += game.getGameNameStr() + " in " + game.getChannel().getName() + ", ";
                        }
                        str = str.substring(0, str.length() - 2);
                    }
                    bot.sendMessage(channel, str);
                } else if (command.equals("blackjack") || command.equals("bj")) {
                    if (!bot.hasGame(channel)) {
                        if (params.length > 0) {
                            bot.startGame(new Blackjack(bot, commandChar, channel, params[0]));
                        } else {
                            bot.startGame(new Blackjack(bot, commandChar, channel));
                        }
                    }
                } else if (command.equals("texaspoker") || command.equals("tp")) {
                    if (!bot.hasGame(channel)) {
                        if (params.length > 0) {
                            bot.startGame(new TexasPoker(bot, commandChar, channel, params[0]));
                        } else {
                            bot.startGame(new TexasPoker(bot, commandChar, channel));
                        }
                    }
                } else if (command.equals("texastourney") || command.equals("tt")) {
                    if (!bot.hasGame(channel)) {
                        if (params.length > 0) {
                            bot.startGame(new TexasTourney(bot, commandChar, channel, params[0]));
                        } else {
                            bot.startGame(new TexasTourney(bot, commandChar, channel));
                        }
                    }
                } else if (command.equals("endgame")) {
                    CardGame game = bot.getGame(channel);
                    if (game != null) {
                        if (game.isInProgress()){
                            bot.sendMessage(channel, "Please wait for the current round to finish.");
                        } else {
                            bot.endGame(game);
                        }
                    }
                } else if (command.equals("shutdown") || command.equals("botquit")) {
                    if (!bot.checkGamesInProgress()){
                        bot.endAllGames();
                        try {
                            bot.quitServer("Bye.");
                        } catch (Exception e){
                            System.out.println("Error: " + e);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Initializes the gameList, sets the log file and other bot settings.
     * @param config the config file
     * @param log the log file
     */
    public CasinoBot(String config, String log){
        super();
        version = "CasinoBot using PircBotX";
        logFile = log;
        gameList = new ArrayList<CardGame>();
        configMap = new HashMap<String,String>();
        setMessageDelay(200);
        
        loadConfig(config);
        
        getListenerManager().addListener(new InitListener('.'));
        setVerbose(true);
        setAutoNickChange(true);
        setAutoReconnect(true);
        setCapEnabled(true);
        setName(configMap.get("nick"));
        setLogin(configMap.get("nick"));
    }
    
    /**
     * Retains the original PircBotX.log() functionality, while adding the 
     * ability to output the log to a file.
     * @param line the line to add to the log
     */
    @Override
    public void log(String line){
        super.log(line);
        if (verbose) {
            try{
                PrintWriter out;
                out = new PrintWriter(new BufferedWriter(new FileWriter(logFile,true)));
                out.println(System.currentTimeMillis() + " " + line);
                out.close();
            } catch(IOException e) {
                System.err.println("Error: unable to write to " + logFile);
            }
        }
    }
    
    /**
     * Patch to eliminate exceptions during shutdown of the bot. Channel caching
     * now only occurs if a reconnect is required.
     * @param noReconnect Toggle whether to reconnect if enabled. Set to true to
     * 100% shutdown the bot
     */
    @Override
    public void shutdown(boolean noReconnect) {
        try {
            outputThread.interrupt();
            inputThread.interrupt();
        } catch (Exception e) {
            logException(e);
        }
        
        //Close the socket from here and let the threads die
        if (!socket.isClosed())
            try {
                socket.shutdownInput();
                socket.close();
            } catch (Exception e) {
                logException(e);
            }
        
        //Close the DCC Manager
        try {
            dccManager.close();
        } catch (Exception ex) {
            //Not much we can do with it here. And throwing it would not let other things shutdown
            logException(ex);
        }
        
        //Clear relevant variables of information
        userChanInfo.clear();
        userNickMap.clear();
        channelListBuilder.finish();
        
        //Dispatch event
        if (autoReconnect && !noReconnect) {
            try {
                reconnect();
            } catch (Exception e) {
                //Not much we can do with it
                throw new RuntimeException("Can't reconnect to server", e);
            }
        } else {
            getListenerManager().dispatchEvent(new DisconnectEvent(this));
            log("*** Disconnected.");
        }
    }
    
    @Override
    public boolean hasGame(Channel channel) {
        CardGame game = getGame(channel);
        if (game == null) {
            return false;
        } else {
            sendMessage(channel, game.getGameNameStr() + " is already running in this channel.");
            return true;
        }
    }
        
    @Override
    public CardGame getGame(Channel channel) {
        for (CardGame game : gameList) {
            if (game.getChannel().equals(channel)){
                return game;
            }
        }
        return null;
    }
    
    @Override
    public CardGame getGame(String nick) {
        for (CardGame game : gameList) {
            if (game.isJoined(nick) || game.isWaitlisted(nick)) {
                return game;
            }
        }
        return null;
    }
    
    @Override
    public boolean isBlacklisted(String nick) {
        for (CardGame game : gameList) {
            if (game.isBlacklisted(nick)) {
                return true;
            }
        }
        return false;
    }
        
    @Override
    public boolean checkGamesInProgress() {
        boolean inProgress = false;
        for (CardGame game : gameList) {
            if (game.isInProgress()){
                sendMessage(game.getChannel(), "A round of " + game.getGameNameStr() + 
                        " is in progress in " + game.getChannel().getName() + 
                        ". Please wait for it to finish.");
                inProgress = true;
            }
        }
        return inProgress;
    }
    
    @Override
    public void startGame(CardGame game) {
        gameList.add(game);
        getListenerManager().addListener(game);
    }
    
    @Override
    public void endGame(CardGame game) {
        game.endGame();
        getListenerManager().removeListener(game);
        gameList.remove(game);
    }
    
    @Override
    public void endAllGames() {
        while(!gameList.isEmpty()){
            endGame(gameList.get(0));
        }
    }
    
    /**
     * Loads configuration file for this bot.
     * 
     * @param configFile the configuration file
     */
    private void loadConfig(String configFile){
        try {
            BufferedReader in = new BufferedReader(new FileReader(configFile));
            String str, key, value;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (!str.startsWith("#") && str.contains("=")) {
                    st = new StringTokenizer(str, "=");
                    key = st.nextToken();
                    value = null;
                    if (st.hasMoreTokens()){
                        value = st.nextToken();
                    }
                    configMap.put(key, value);
                }
            }
            in.close();
        } catch (IOException e) {
            /* load defaults if config file is not found */
            log(configFile + " not found! Loading default values...");
            configMap.put("nick", "CasinoBot");
            configMap.put("network", "chat.freenode.net");
            configMap.put("channel", "##CasinoBot");
            configMap.put("password", null);
            /* Write these values to a new file */
            try{
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(configFile)));
                out.println("#Bot nick");
                out.println("nick=" + configMap.get("nick"));
                out.println("#SASL password");
                out.println("password=");
                out.println("#IRC network");
                out.println("network=" + configMap.get("network"));
                out.println("#IRC channel");
                out.println("channel=" + configMap.get("channel"));
                out.close();
            } catch (IOException f) {
                log("Error creating " + configFile + "!");
            }
        }
    }
    
    /**
     * Creates and initializes a new CasinoBot and connects it to an IRC network.
     * @param args alternate config file path
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        CasinoBot bot;
        if (args.length > 0) {
            bot = new CasinoBot(args[0], "log.txt");
        } else {
            bot = new CasinoBot("irccasino.conf", "log.txt");
        }
        
        if (bot.configMap.get("password") != null){
            bot.getCapHandlers().add(new SASLCapHandler(bot.configMap.get("nick"), bot.configMap.get("password")));
        }
        
        try {
            bot.connect(bot.configMap.get("network"));
        } catch (IrcException e){
            System.out.println("Error: " + e);
        } catch (IOException e){
            System.out.println("Error: " + e);
        }
    }
}
