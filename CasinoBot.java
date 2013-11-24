/*
    Copyright (C) 2013 Yizhe Shen <brrr@live.ca>

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringTokenizer;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.managers.ThreadedListenerManager;

/**
 * The default standalone bot that allows any number of games, each in their
 * own channel. It also logs all activity to log.txt.
 * @author Yizhe Shen
 */
public class CasinoBot extends PircBotX implements GameManager {
    
    public static CasinoBot bot;
    public static String configFile;
    public static String botNick, botPassword, network;
    public static ArrayList<String> channelList;
    public ArrayList<CardGame> gameList;
    public String logFile;
    
    /**
     * Listener for CasinoBot initialization 
     */
    public static class InitListener extends ListenerAdapter<PircBotX> {
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
        public void onConnect(ConnectEvent<PircBotX> event){
            for (int ctr = 0; ctr < channelList.size(); ctr++){
                bot.joinChannel(channelList.get(ctr));
            }
        }
        
        /**
         * Parses channel messages for commands and parameters.
         * @param event Message event
         */
        @Override
        public void onMessage(MessageEvent<PircBotX> event){
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
                processCommand(event.getChannel(), event.getUser(), command, params);
            }
        }

        /**
         * Processes initialization commands for this bot.
         * 
         * @param channel the originating channel of the command
         * @param user the user who gave the command
         * @param command the command
         * @param params the parameters after the command
         */
        public void processCommand(Channel channel, User user, String command, String[] params){
            if (channel.isOp(user)){
                if (command.equals("games")) {
                    CardGame game;
                    String str = "Currently running: ";
                    if (bot.gameList.isEmpty()) {
                        str += "No games";
                    } else {
                        for (int ctr = 0; ctr < bot.gameList.size(); ctr++){
                            game = bot.gameList.get(ctr);
                            str += game.getGameNameStr() + " in " + game.getChannel().getName() + ", ";
                        }
                        str = str.substring(0, str.length() - 2);
                    }
                    bot.sendMessage(channel, str);
                } else if (command.equals("blackjack") || command.equals("bj")) {
                    if (!bot.hasGame(channel)) {
                        Blackjack newGame;
                        if (params.length > 0) {
                            newGame = new Blackjack(bot, commandChar, channel, params[0]);
                        } else {
                            newGame = new Blackjack(bot, commandChar, channel);
                        }
                        bot.gameList.add(newGame);
                        bot.getListenerManager().addListener(newGame);
                    }
                } else if (command.equals("texaspoker") || command.equals("tp")) {
                    if (!bot.hasGame(channel)) {
                        TexasPoker newGame;
                        if (params.length > 0) {
                            newGame = new TexasPoker(bot, commandChar, channel, params[0]);
                        } else {
                            newGame = new TexasPoker(bot, commandChar, channel);
                        }
                        bot.gameList.add(newGame);
                        bot.getListenerManager().addListener(newGame);
                    }
                } else if (command.equals("endgame")) {
                    CardGame game = bot.getGame(channel);
                    if (game != null) {
                        if (game.has("inprogress")){
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
     * @param fileName the file path to the log file
     */
    public CasinoBot(String fileName){
        super();
        version = "CasinoBot using PircBotX";
        logFile = fileName;
        gameList = new ArrayList<CardGame>();
        setMessageDelay(200);
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
        for (int ctr = 0; ctr < gameList.size(); ctr++){
            if (gameList.get(ctr).getChannel().equals(channel)){
                return gameList.get(ctr);
            }
        }
        return null;
    }
    
    @Override
    public CardGame getGame(String nick) {
        CardGame game;
        for (int ctr = 0; ctr < gameList.size(); ctr++) {
            game = gameList.get(ctr);
            if (game.isJoined(nick) || game.isWaitlisted(nick)) {
                return game;
            }
        }
        return null;
    }
    
    @Override
    public boolean isBlacklisted(String nick) {
        CardGame game;
        for (int ctr = 0; ctr < gameList.size(); ctr++) {
            game = gameList.get(ctr);
            if (game.isBlacklisted(nick)) {
                return true;
            }
        }
        return false;
    }
        
    @Override
    public boolean checkGamesInProgress() {
        boolean inProgress = false;
        CardGame game;
        for (int ctr = 0; ctr < gameList.size(); ctr++){
            game = gameList.get(ctr);
            if (game.has("inprogress")){
                sendMessage(game.getChannel(), "A round of " + game.getGameNameStr() + 
                        " is in progress in " + game.getChannel().getName() + 
                        ". Please wait for it to finish.");
                inProgress = true;
            }
        }
        return inProgress;
    }
        
    @Override
    public void endAllGames() {
        for (int ctr = 0; ctr < gameList.size(); ctr++){
            endGame(gameList.get(ctr));
            ctr--;
        }
    }
    
    @Override
    public void endGame(CardGame game) {
        game.endGame();
        getListenerManager().removeListener(game);
        gameList.remove(game);
    }
    
    /**
     * Loads configuration file for this bot.
     * 
     * @param configFile File path of the configuration file
     */
    public static void loadConfig(String configFile){
        try {
            BufferedReader in = new BufferedReader(new FileReader(configFile));
            String str, name, value;
            StringTokenizer st;
            while (in.ready()) {
                str = in.readLine();
                if (str.startsWith("#")) {
                    continue;
                }
                st = new StringTokenizer(str, "=");
                name = st.nextToken();
                if (st.hasMoreTokens()){
                    value = st.nextToken();
                } else {
                    value = null;
                }
                if (name.equals("nick")) {
                    botNick = value;
                } else if (name.equals("password")) {
                    botPassword = value;
                } else if (name.equals("channel")) {
                    StringTokenizer st2 = new StringTokenizer(value, ",");
                    while (st2.hasMoreTokens()){
                        channelList.add(st2.nextToken());
                    }
                } else if (name.equals("network")) {
                    network = "chat.freenode.net";
                }
            }
            in.close();
        } catch (IOException e) {
            /* load defaults if config file is not found */
            bot.log(configFile + " not found! Loading default values...");
            botNick = "CasinoBot";
            network = "chat.freenode.net";
            channelList.add("##CasinoBot");
            botPassword = null;
            /* Write these values to a new file */
            try{
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(configFile)));
                out.println("#Bot nick");
                out.println("nick=" + botNick);
                out.println("#SASL password");
                out.println("password=");
                out.println("#IRC network");
                out.println("network=" + network);
                out.println("#IRC channel");
                out.println("channel=" + channelList.get(0));
                out.close();
            } catch (IOException f) {
                bot.log("Error creating " + configFile + "!");
            }
        }
    }
    
    /**
     * Creates and initializes a new CasinoBot and connects it to an IRC network.
     * @param args alternate config file path
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        bot = new CasinoBot("log.txt");
        channelList = new ArrayList<String>();
        if (args.length > 0) {
            loadConfig(args[0]);
        } else {
            loadConfig("irccasino.conf");
        }
        InitListener init = new InitListener(bot, '.');
        ThreadedListenerManager<PircBotX> manager = new ThreadedListenerManager<PircBotX>();
        
        manager.addListener(init);
        bot.setListenerManager(manager);
        bot.setVerbose(true);
        bot.setAutoNickChange(true);
        bot.setAutoReconnect(true);
        bot.setCapEnabled(true);
        bot.setName(botNick);
        bot.setLogin(botNick);
        
        if (botPassword != null){
            bot.getCapHandlers().add(new SASLCapHandler(botNick, botPassword));
        }
        
        try {
            bot.connect(network);
        } catch (IrcException e){
            System.out.println("Error: " + e);
        } catch (IOException e){
            System.out.println("Error: " + e);
        }
    }
}
