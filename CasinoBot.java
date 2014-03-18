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
import java.util.List;
import java.util.StringTokenizer;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.cap.SASLCapHandler;
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
     * Listener for CasinoBot initialization commands.
     */
    public static class InitListener extends ListenerAdapter<CasinoBot> {
        protected CasinoBot bot;
        protected char commandChar;
        
        public InitListener(CasinoBot parent, char commChar) {
            bot = parent;
            commandChar = commChar;
        }
        
        /**
         * Joins the channels in the config file upon successful connection.
         * @param event Connect event
         */
        @Override
        public void onConnect(ConnectEvent<CasinoBot> event){
            Channel chan;
            StringTokenizer st;
            
            // Auto-join channels from config
            st = new StringTokenizer(bot.configMap.get("channel"), ", ");
            while(st.hasMoreTokens()) {
                bot.joinChannel(st.nextToken());
            }
            
            // Auto-start Blackjack
            st = new StringTokenizer(bot.configMap.get("bjchannel"), ", ");
            while(st.hasMoreTokens()) {
                chan = bot.getChannel(st.nextToken());
                blackjack(chan, null, new String[0], null);
            }
            
            // Auto-start TexasPoker
            st = new StringTokenizer(bot.configMap.get("tpchannel"), ", ");
            while(st.hasMoreTokens()) {
                chan = bot.getChannel(st.nextToken());
                texaspoker(chan, null, new String[0], null);
            }
            
            // Auto-start TexasTourney
            st = new StringTokenizer(bot.configMap.get("ttchannel"), ", ");
            while(st.hasMoreTokens()) {
                chan = bot.getChannel(st.nextToken());
                texastourney(chan, null, new String[0], null);
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
                String command = st.nextToken();
                String[] params = new String[st.countTokens()];
                for (int ctr = 0; ctr < params.length; ctr++){
                    params[ctr] = st.nextToken();
                }
                processCommand(event.getChannel(), event.getUser(), command, params, msg);
            }
        }

        /**
         * Processes initialization commands for a CasinoBot.
         * @param channel the originating channel of the command
         * @param user the user who gave the command
         * @param command the command
         * @param params the parameters after the command
         * @param msg
         */
        public void processCommand(Channel channel, User user, String command, String[] params, String msg){
            if (!channel.isOp(user)){
                return; // Do nothing if not a channel Op
            }
            
            if (command.equalsIgnoreCase("games")) {
                games(channel, user, params, msg);
            } else if (command.equalsIgnoreCase("blackjack") || command.equalsIgnoreCase("bj")) {
                blackjack(channel, user, params, msg);
            } else if (command.equalsIgnoreCase("texaspoker") || command.equalsIgnoreCase("tp")) {
                texaspoker(channel, user, params, msg);
            } else if (command.equalsIgnoreCase("texastourney") || command.equalsIgnoreCase("tt")) {
                texastourney(channel, user, params, msg);
            } else if (command.equalsIgnoreCase("endgame")) {
                endGame(channel, user, params, msg);
            } else if (command.equalsIgnoreCase("botquit") || command.equalsIgnoreCase("shutdown")) {
                botQuit(channel, user, params, msg);
            } else if (command.equalsIgnoreCase("reboot") || command.equalsIgnoreCase("reconnect")) {
                reboot(channel, user, params, msg);
            }
        }
        
        /**
         * Displays a list of games the bot is currently running.
         * @param channel 
         * @param user 
         * @param params 
         * @param msg 
         */
        public void games(Channel channel, User user, String[] params, String msg) {
            String str;
            if (!bot.hasGames()) {
                str = "No games are running.";
            } else {
                str = "Games: ";
                for (CardGame game : bot.getGames()) {
                    str += game.getGameNameStr() + " in " + game.getChannel().getName() + ", ";
                }
                str = str.substring(0, str.length() - 2);
            }
            bot.sendMessage(channel, str);
        }
        
        /**
         * Starts a new game of Blackjack.
         * @param channel
         * @param user
         * @param params 
         * @param msg 
         */
        public void blackjack(Channel channel, User user, String[] params, String msg) {
            if (bot.hasGame(channel)) {
                bot.sendMessage(channel, bot.getGame(channel).getGameNameStr() + " is already running in this channel.");
            } else {
                if (params.length > 0) {
                    bot.startGame(new Blackjack(bot, commandChar, channel, params[0]));
                } else {
                    bot.startGame(new Blackjack(bot, commandChar, channel));
                }
            }
        }
        
        /**
         * Starts a new game of Texas Hold'em.
         * @param channel
         * @param user
         * @param params 
         * @param msg 
         */
        public void texaspoker(Channel channel, User user, String[] params, String msg) {
            if (bot.hasGame(channel)) {
                bot.sendMessage(channel, bot.getGame(channel).getGameNameStr() + " is already running in this channel.");
            } else {
                if (params.length > 0) {
                    bot.startGame(new TexasPoker(bot, commandChar, channel, params[0]));
                } else {
                    bot.startGame(new TexasPoker(bot, commandChar, channel));
                }
            }
        }
        
        /**
         * Starts a new game of Texas Hold'em tournament.
         * @param channel
         * @param user
         * @param params 
         * @param msg 
         */
        public void texastourney(Channel channel, User user, String[] params, String msg) {
            if (bot.hasGame(channel)) {
                bot.sendMessage(channel, bot.getGame(channel).getGameNameStr() + " is already running in this channel.");
            } else {
                if (params.length > 0) {
                    bot.startGame(new TexasTourney(bot, commandChar, channel, params[0]));
                } else {
                    bot.startGame(new TexasTourney(bot, commandChar, channel));
                }
            }
        }
        
        /**
         * Ends the game in the specified channel.
         * @param channel 
         * @param user 
         * @param params 
         * @param msg 
         */
        public void endGame(Channel channel, User user, String[] params, String msg) {
            if (!bot.hasGame(channel)) {
                bot.sendMessage(channel, "No game is currently running.");
            } else {
                CardGame game = bot.getGame(channel);
                if (game.isInProgress()){
                    bot.sendMessage(channel, "Please wait for the current round to finish.");
                } else {
                    bot.endGame(game);
                }
            }
        }
        
        /**
         * Disconnects the CasinoBot and ends all games.
         * @param channel
         * @param user
         * @param params
         * @param msg
         */
        public void botQuit(Channel channel, User user, String[] params, String msg) {
            if (bot.gamesInProgress()) {
                bot.sendMessage(channel, "There is a game in progress. Please wait for it to finish.");
            } else {
                bot.endAllGames();
                bot.setAutoReconnect(false);
                bot.quitServer("Bye.");
            }
        }
        
        /**
         * Reconnects the CasinoBot to the IRC server.
         * @param channel
         * @param user
         * @param params
         * @param msg
         */
        public void reboot(Channel channel, User user, String[] params, String msg) {
            if (bot.gamesInProgress()) {
                bot.sendMessage(channel, "There is a game in progress. Please wait for it to finish.");
            } else {
                bot.quitServer("Reconnecting...");
            }
        }
    }
    
    /**
     * Initializes custom CasinoBot fields.
     */
    public CasinoBot(){
        super();
        logFile = "";
        gameList = new ArrayList<CardGame>();
        configMap = new HashMap<String,String>();
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
     * Patch to eliminate exceptions during shutdown of the bot. All channel
     * caching has been removed. 
     * @param noReconnect Toggle whether to reconnect if enabled. Set to true to
     * 100% shutdown the bot
     */
    @Override
    public void shutdown(boolean noReconnect) {
        try {
            if (outputThread != null) outputThread.interrupt();
            if (inputThread != null) inputThread.interrupt();
        } catch (Exception e) {
            logException(e);
        }
        
        //Close the socket from here and let the threads die
        if (socket != null && !socket.isClosed())
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
        getListenerManager().dispatchEvent(new DisconnectEvent(this));
        log("*** Disconnected.");
    }
    
    @Override
    public boolean hasGame(Channel channel) {
        return getGame(channel) != null;
    }
    
    @Override
    public boolean hasGames() {
        return !gameList.isEmpty();
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
    public List<CardGame> getGames() {
        return gameList;
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
    public boolean gamesInProgress() {
        for (CardGame game : gameList) {
            if (game.isInProgress()){
                return true;
            }
        }
        return false;
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
     * @param configFile the configuration file
     */
    protected void loadConfig(String configFile){
        try {
            BufferedReader in = new BufferedReader(new FileReader(configFile));
            String str, key, value;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (!str.startsWith("#") && str.contains("=")) {
                    st = new StringTokenizer(str, "=");
                    key = st.nextToken();
                    value = "";
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
            configMap.put("password", "");
            configMap.put("bjchannel", "");
            configMap.put("tpchannel", "");
            configMap.put("ttchannel", "");
            saveConfig(configFile);
        }
    }
    
    /**
     * Saves configuration file for this bot.
     * @param configFile 
     */
    protected void saveConfig(String configFile) {
        /* Write these values to a new file */
        try{
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(configFile)));
            out.println("#Bot nick");
            out.println("nick=" + configMap.get("nick"));
            out.println("#SASL password");
            out.println("password=" + configMap.get("password"));
            out.println("#IRC network");
            out.println("network=" + configMap.get("network"));
            out.println("#IRC channel");
            out.println("channel=" + configMap.get("channel"));
            out.println("#Blackjack channel");
            out.println("bjchannel=" + configMap.get("bjchannel"));
            out.println("#Texas Hold'em channel");
            out.println("tpchannel=" + configMap.get("tpchannel"));
            out.println("#Texas Hold'em Tournament channel");
            out.println("ttchannel=" + configMap.get("ttchannel"));
            out.close();
        } catch (IOException f) {
            log("Error creating " + configFile + "!");
        }
    }
    
    /**
     * Initializes bot with custom parameters.
     */
    protected void initBot(String config, String log) {
        version = "CasinoBot using PircBotX";
        logFile = log;
        setMessageDelay(200);
        
        loadConfig(config);
        
        setVerbose(true);
        setAutoNickChange(true);
        setAutoReconnect(true);
        setCapEnabled(true);
        setName(configMap.get("nick"));
        setLogin(configMap.get("nick"));
        
        // Add listener for initialization commands
        getListenerManager().addListener(new InitListener(this, '.'));
    }
    
    /**
     * Attempts to connect the bot to an IRC server.
     * Attempts to reconnect upon failure or disconnection.
     * @throws Exception 
     */
    protected void runBot() throws Exception{
        int timeInt = 10000;    // milliseconds
        int attempt = 0;
        
        // Continue trying to connect to the server if not in a connected state
        while (!isConnected()) {
            try {
                attempt++;
                if (!configMap.get("password").equals("")){
                    getCapHandlers().clear();
                    getCapHandlers().add(new SASLCapHandler(configMap.get("nick"), configMap.get("password")));
                }
                
                // Reset IRC threads
                inputThread = null;
                outputThread = null;
                
                // Attempt to connect
                log("Connection attempt " + attempt + "...");
                connect(configMap.get("network"));
            } catch (Exception e){
                // Log the exception that caused connection failure
                logException(e);
                
                // Wait for IRC threads to die
                if (inputThread != null) inputThread.join();
                if (outputThread != null) outputThread.join();
                
                // Set delay up to 600 seconds or 10 minutes
                int delay = Math.min(attempt * timeInt, timeInt * 60);
                log("Attempt to reconnect in " + (delay/1000) + " seconds...");
                Thread.sleep(delay);
                
                // Retry
                continue;
            }
            
            // If we make it here that means we're connected, reset attempts
            attempt = 0;
            
            // Wait for any disconnections
            inputThread.join();
            outputThread.join();
            
            // Terminate if no auto-reconnect is required
            if (!autoReconnect) {
                break;
            }
            
            // Otherwise wait the 10 seconds to reconnect
            Thread.sleep(timeInt);
        }
    }
    
    /**
     * Creates and initializes a new CasinoBot and connects it to an IRC network.
     * Also attempts reconnect on connection failure or disconnection.
     * @param args alternate config file path
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        CasinoBot bot = new CasinoBot();
        
        // Check for alternate config file
        if (args.length > 0) {
            bot.initBot(args[0], "log.txt");
        } else {
            bot.initBot("irccasino.conf", "log.txt");
        }
        
        bot.runBot();
    }
}
