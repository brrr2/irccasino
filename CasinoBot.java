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
 * A sample bot that will load an instance of each game in its own channel.
 * It also logs all activity to log.txt.
 * @author Yizhe Shen
 */
public class CasinoBot extends PircBotX {
    /* Listener for CasinoBot initialization */
    public static class InitListener extends ListenerAdapter<PircBotX> {
        CasinoBot bot;
        char commandChar;
        public InitListener(CasinoBot parent, char commChar) {
            bot = parent;
            commandChar = commChar;
        }
        
        @Override
        public void onConnect(ConnectEvent<PircBotX> event){
            for (int ctr = 0; ctr < channelList.size(); ctr++){
                bot.joinChannel(channelList.get(ctr));
            }
        }
        
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
         * Processes commands for this bot.
         * 
         * @param channel the originating channel of the command
         * @param user the user who gave the command
         * @param command the command
         * @param params the parameters after the command
         */
        public void processCommand(Channel channel, User user, String command, String[] params){
            if (channel.isOp(user)){
                CasinoBot cbot = (CasinoBot) bot;
                if (command.equals("blackjack") || command.equals("bj")) {
                    if (cbot.tpgame != null && cbot.tpgame.getChannel().equals(channel)){
                        bot.sendMessage(channel, "Currently running "+cbot.tpgame.getGameNameStr()+" in this channel.");
                    } else if (cbot.bjgame != null) {
                        bot.sendMessage(channel, cbot.bjgame.getGameNameStr()+" is already running in "+cbot.bjgame.getChannel().getName());
                    } else {
                        cbot.bjgame = new Blackjack(cbot, commandChar, channel);
                        cbot.getListenerManager().addListener(cbot.bjgame);
                    }
                } else if (command.equals("texaspoker") || command.equals("tp")) {
                    if (cbot.bjgame != null && cbot.bjgame.getChannel().equals(channel)){
                        bot.sendMessage(channel, "Currently running "+cbot.bjgame.getGameNameStr()+" in this channel.");
                    } else if (cbot.tpgame != null) {
                        bot.sendMessage(channel, cbot.tpgame.getGameNameStr()+" is already running in "+cbot.tpgame.getChannel().getName());
                    } else {
                        cbot.tpgame = new TexasPoker(cbot, commandChar, channel);
                        cbot.getListenerManager().addListener(cbot.tpgame);
                    }
                } else if (command.equals("endgame")) {
                    if (cbot.bjgame != null && cbot.bjgame.getChannel().equals(channel)){
                        if (cbot.bjgame.has("inprogress")){
                            bot.sendMessage(channel, "Please wait for the current round to finish.");
                        } else {
                            cbot.bjgame.endGame();
                            cbot.getListenerManager().removeListener(cbot.bjgame);
                            cbot.bjgame = null;
                        }
                    }
                    if (cbot.tpgame != null && cbot.tpgame.getChannel().equals(channel)){
                        if (cbot.tpgame.has("inprogress")){
                            bot.sendMessage(channel, "Please wait for the current round to finish.");
                        } else {
                            cbot.tpgame.endGame();
                            cbot.getListenerManager().removeListener(cbot.tpgame);
                            cbot.tpgame = null;
                        }
                    }
                } else if (command.equals("shutdown") || command.equals("botquit")) {
                    if (cbot.bjgame != null || cbot.tpgame != null){
                        if (cbot.bjgame != null && cbot.bjgame.has("inprogress")){
                            bot.sendMessage(channel, "A round of "+cbot.bjgame.getGameNameStr()+" is in progress. Please wait for it to finish.");
                        } else if (cbot.tpgame != null && cbot.tpgame.has("inprogress")){
                            bot.sendMessage(channel, "A round of "+cbot.tpgame.getGameNameStr()+" is in progress. Please wait for it to finish.");
                        } else {
                            if (cbot.bjgame != null){
                                cbot.bjgame.endGame();
                                cbot.getListenerManager().removeListener(cbot.bjgame);
                                cbot.bjgame = null;
                            }
                            if (cbot.tpgame != null){
                                cbot.tpgame.endGame();
                                cbot.getListenerManager().removeListener(cbot.tpgame);
                                cbot.tpgame = null;
                            }
                            try {
                                bot.quitServer("Bye.");
                            } catch (Exception e){
                                System.out.println("Error: " + e);
                            }
                        }
                    } else {
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
    
    public static CasinoBot bot;
    public static String configFile;
    public static String botNick, botPassword, network;
    public static ArrayList<String> channelList;
    public CardGame bjgame, tpgame;
    public String logFile;
    
    public CasinoBot(String fileName){
        super();
        version = "CasinoBot using PircBotX";
        logFile = fileName;
        bjgame = null;
        tpgame = null;
        setMessageDelay(200);
    }
    
    @Override
    public void log(String line){
        if (!verbose) { return; }
        super.log(line);
        try{
            PrintWriter out;
            out = new PrintWriter(new BufferedWriter(new FileWriter(logFile,true)));
            out.println(System.currentTimeMillis() + " " + line);
            out.close();
        } catch(IOException e) {
            System.err.println("Error: unable to write to "+logFile);
        }
    }
    
    public static void main(String[] args) throws Exception {
        bot = new CasinoBot("log.txt");
        channelList = new ArrayList<String>();
        loadConfig("irccasino.conf");
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
        } catch (IrcException | IOException e){
            System.out.println("Error: " + e);
        }
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
                    if (value == null){
                        botNick = "CasinoBot";
                    } else {
                        botNick = value;
                    }
                } else if (name.equals("password")) {
                    botPassword = value;
                } else if (name.equals("channel")) {
                    if (value == null){
                        channelList.add("##CasinoBot");
                    } else {
                        StringTokenizer st2 = new StringTokenizer(value, ",");
                        while (st2.hasMoreTokens()){
                            channelList.add(st2.nextToken());
                        }
                    }
                } else if (name.equals("network")) {
                    if (value == null){
                        network = "chat.freenode.net";
                    } else {
                        network = value;
                    }
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
                bot.log("Error creating "+configFile+"!");
            }
        }
    }
}
