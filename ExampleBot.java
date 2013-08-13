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
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.managers.ThreadedListenerManager;

public class ExampleBot extends ListenerAdapter<PircBotX> {
    /* A PircBotX bot that also logs to a file */
    public static class FileLogBot extends PircBotX{
        public String logFile;
        public FileLogBot(String fileName){
            super();
            version = "Casino Bot using PircBotX";
            logFile = fileName;
            setMessageDelay(750);
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
    }
    
	public static FileLogBot bot;
	public CardGame bjgame, tpgame;
    public String configFile;
	public String botNick, password, network;
    public ArrayList<String> channelList;
	public char commandChar = '.';
    
    public ExampleBot(){
        channelList = new ArrayList<String>();
        loadConfig("irccasino.conf");
        bjgame = null;
        tpgame = null;
    }
    
	public static void main(String[] args) throws Exception {
        ExampleBot eb = new ExampleBot();
        ThreadedListenerManager manager = new ThreadedListenerManager();
        manager.addListener(eb);
	    bot = new FileLogBot("log.txt");
        bot.setListenerManager(manager);
	    bot.setVerbose(true);
	    bot.setAutoNickChange(true);
        bot.setAutoReconnect(true);
	    bot.setCapEnabled(true);
        bot.setName(eb.botNick);
        bot.setLogin("bot");
        
		if (eb.password != null){
            bot.getCapHandlers().add(new SASLCapHandler(eb.botNick, eb.password));
        }
        bot.connect(eb.network);
	}
	
    /**
     * Loads configuration file for this bot.
     * @param configFile file path of the configuration file
     */
    public final void loadConfig(String configFile){
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
                        botNick = "ExampleBot";
                    } else {
                        botNick = value;
                    }
				} else if (name.equals("password")) {
					password = value;
				} else if (name.equals("channel")) {
                    if (value == null){
                        channelList.add("##ExampleBot");
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
			bot.log(configFile+" not found! Loading default values...");
            botNick = "ExampleBot";
            network = "chat.freenode.net";
            channelList.add("##ExampleBot");
            password = null;
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
    
	@Override
    public void onMessage (MessageEvent<PircBotX> event){
        String msg = event.getMessage();
        Channel channel = event.getChannel();
        User user = event.getUser();
        
        if (msg.length() > 1 && msg.charAt(0) == commandChar){
            msg = msg.substring(1);
            StringTokenizer st = new StringTokenizer(msg);
            String command = st.nextToken().toLowerCase();
            String[] params = new String[st.countTokens()];
            for (int ctr = 0; ctr < params.length; ctr++){
                params[ctr] = st.nextToken();
            }
            
            // Process commands
            processCommand(channel, user, command, params);
            
            // Process any game command if a game has been instantiated
            if (bjgame != null && bjgame.getChannel() == channel){
                bjgame.processCommand(user, command, params);
            }
            if (tpgame != null && tpgame.getChannel() == channel){
                tpgame.processCommand(user, command, params);
            }
        }
    }
	
	@Override
    public void onConnect(ConnectEvent<PircBotX> event){
        for (int ctr = 0; ctr < channelList.size(); ctr++){
            bot.joinChannel(channelList.get(ctr));
        }
    }
    
    @Override
    public void onJoin(JoinEvent<PircBotX> event){
        Channel channel = event.getChannel();
        if (bjgame != null && bjgame.getChannel() == channel){
            bjgame.processJoin(event.getUser());
        }
        if (tpgame != null && tpgame.getChannel() == channel){
            tpgame.processJoin(event.getUser());
        }
    }
    
    @Override
    public void onPart(PartEvent<PircBotX> event){
        Channel channel = event.getChannel();
        if (bjgame != null && bjgame.getChannel() == channel){
            bjgame.processQuit(event.getUser());
        }
        if (tpgame != null && tpgame.getChannel() == channel){
            tpgame.processQuit(event.getUser());
        }
    }
    
    @Override
    public void onQuit(QuitEvent<PircBotX> event){
        if (bjgame != null){
            bjgame.processQuit(event.getUser());
        }
        if (tpgame != null){
            tpgame.processQuit(event.getUser());
        }
    }
    
    @Override
    public void onNickChange(NickChangeEvent<PircBotX> event){
        if (bjgame != null){
            bjgame.processNickChange(event.getUser(), event.getOldNick(), event.getNewNick());
        }
        if (tpgame != null){
            tpgame.processNickChange(event.getUser(), event.getOldNick(), event.getNewNick());
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
            if (command.equals("blackjack") || command.equals("bj")) {
                if (tpgame != null && tpgame.getChannel() == channel){
                    bot.sendMessage(channel, "Currently running "+tpgame.getGameNameStr()+" in this channel.");
                } else if (bjgame != null) {
                    bot.sendMessage(channel, bjgame.getGameNameStr()+" is already running in "+bjgame.getChannel().getName());
                } else {
                    bjgame = new Blackjack(bot, channel, this);
                    bjgame.showGameStart();
                }
            } else if (command.equals("texaspoker") || command.equals("tp")) {
                if (bjgame != null && bjgame.getChannel() == channel){
                    bot.sendMessage(channel, "Currently running "+bjgame.getGameNameStr()+" in this channel.");
                } else if (tpgame != null) {
                    bot.sendMessage(channel, tpgame.getGameNameStr()+" is already running in "+tpgame.getChannel().getName());
                } else {
                    tpgame = new TexasPoker(bot, channel, this);
                    tpgame.showGameStart();
                }
            } else if (command.equals("endgame")) {
                if (bjgame != null && channel == bjgame.getChannel()){
                    if (bjgame.isInProgress()){
                        bot.sendMessage(channel, "Please wait for the current round to finish.");
                    } else {
                        bjgame.endGame();
                        bjgame = null;
                    }
                }
                if (tpgame != null && tpgame.getChannel() == channel){
                    if (tpgame.isInProgress()){
                        bot.sendMessage(channel, "Please wait for the current round to finish.");
                    } else {
                        tpgame.endGame();
                        tpgame = null;
                    }
                }
            } else if (command.equals("shutdown") || command.equals("botquit")) {
                if (bjgame != null || tpgame != null){
                    if (bjgame != null && bjgame.isInProgress()){
                        bot.sendMessage(channel, "A round of "+bjgame.getGameNameStr()+" is in progress. Please wait for it to finish.");
                    } else if (tpgame != null && tpgame.isInProgress()){
                        bot.sendMessage(channel, "A round of "+tpgame.getGameNameStr()+" is in progress. Please wait for it to finish.");
                    } else {
                        if (bjgame != null){
                            bjgame.endGame();
                            bjgame = null;
                        }
                        if (tpgame != null){
                            tpgame.endGame();
                            tpgame = null;
                        }
                        bot.quitServer("Bye!");
                    }
                } else {
                    bot.quitServer("Bye!");
                }
            }
        }
    }
}
