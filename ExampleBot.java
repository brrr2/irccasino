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
import java.util.StringTokenizer;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;

public class ExampleBot extends ListenerAdapter<PircBotX> {
    /* A PircBotX bot that also logs to a file */
    public static class FileLogBot extends PircBotX{
        public String logFile;
        public FileLogBot(String fileName){
            super();
            logFile = fileName;
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
                bot.log("Error: unable to write to "+logFile);
            }
        }
    }
    
	public static FileLogBot bot;
	public static CardGame game;
    public static String configFile;
	public static String botNick, password, network, channelStr;
	public static char commandChar = '.';
	    
	public static void main(String[] args) throws Exception {
	    bot = new FileLogBot("log.txt");
        bot.getListenerManager().addListener(new ExampleBot());
	    bot.setVerbose(true);
	    bot.setAutoNickChange(true);
	    bot.setCapEnabled(true);
        loadConfig("irccasino.conf");
        bot.setName(botNick);
        bot.setLogin("bot");
        
		if (password != null){
            bot.getCapHandlers().add(new SASLCapHandler(botNick, password));
        }
        bot.connect(network);
	}
	
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
					password = value;
				} else if (name.equals("channel")) {
					channelStr = value;
				} else if (name.equals("network")) {
					network = value;
				}
			}
			in.close();
		} catch (IOException e) {
			/* load defaults if config file is not found */
			bot.log(configFile+" not found! Loading default values...");
            botNick = "ExampleBot";
            network = "chat.freenode.net";
            channelStr = "##ExampleBot";
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
                out.println("channel=" + channelStr);
                out.close();
            } catch (IOException f) {
                bot.log("Error creating "+configFile+"!");
            }
        }
    }
    
	@Override
    public void onMessage (MessageEvent<PircBotX> event){
        String origMsg = event.getMessage();
        String msg = origMsg.toLowerCase();
        Channel channel = event.getChannel();
        User user = event.getUser();
        if (msg.length() > 1 && msg.charAt(0) == commandChar){
        	msg = msg.substring(1);
            origMsg = origMsg.substring(1);
            if (channel.isOp(user)){
	            if (msg.equals("blackjack") || msg.equals("bj")) {
	                if (game == null){
	                	game = new Blackjack(event.getBot(), channel);
	                	game.showGameStart();
	                } else {
	                    bot.sendMessage(channel,"A game is already running!");
	                }
	            } else if (msg.equals("texaspoker") || msg.equals("tp")) {
	            	if (game == null){
	                	game = new TexasPoker(event.getBot(), channel);
	                	game.showGameStart();
	                } else {
	                    bot.sendMessage(channel,"A game is already running!");
	                }
	            } else if (msg.equals("endgame")) {
	            	if (game == null){
	                    bot.sendMessage(channel, "No game is currently running!");
	                } else {
	                	if (game.isInProgress()){
	                		bot.sendMessage(channel, "Please wait for the current round to finish.");
	                	} else {
	                		game.endGame();
		                    game = null;
	                	}
	                }
	            } else if (msg.equals("shutdown") || msg.equals("botquit")) {
	            	if (game != null){
	                	if (game.isInProgress()){
	                		bot.sendMessage(channel, "Please wait for the current round to finish.");
	                	} else {
	                		game.endGame();
		                    game = null;
			                bot.getListenerManager().removeListener(this);
			                bot.quitServer();
	                	}
	                } else {
		                bot.getListenerManager().removeListener(this);
		                bot.quitServer();
	                }
	            }
            }
            // Process any game message if a game has been instantiated
            if (game != null){
                game.processMessage(user, msg, origMsg);
            }
        }
    }
	
	@Override
    public void onConnect(ConnectEvent<PircBotX> event){
		bot.joinChannel(channelStr);
    }
    
    @Override
    public void onJoin(JoinEvent<PircBotX> event){
        if (game != null){
            game.processJoin(event.getUser());
        }
    }
    
    @Override
    public void onPart(PartEvent<PircBotX> event){
        if (game != null){
            game.processQuit(event.getUser());
        }
    }
    
    @Override
    public void onQuit(QuitEvent<PircBotX> event){
        if (game != null){
            game.processQuit(event.getUser());
        }
    }
    
    @Override
    public void onNickChange(NickChangeEvent<PircBotX> event){
        if (game != null){
            game.processNickChange(event.getUser(), event.getOldNick(), event.getNewNick());
        }
    }
}
