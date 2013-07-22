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


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.managers.*;

public class ExampleBot extends ListenerAdapter<PircBotX> {
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
                System.out.println("Error: unable to write to "+logFile);
            }
        }
    }
    
	public static FileLogBot bot;
	public static ThreadedListenerManager<PircBotX> manager;
	public static CardGame game;
    public static String configFile;
	public static String botNick, password, network, ircchannel;
	public static final char commandChar = '.';
	    
	public static void main(String[] args) throws Exception {
		 //Create Listener Manager to use
	    manager = new ThreadedListenerManager<PircBotX>();
	    manager.addListener(new ExampleBot());
	    
	    bot = new FileLogBot("log.txt");
	    bot.setListenerManager(manager);
	    bot.setVerbose(true);
	    bot.setAutoNickChange(true);
	    bot.setCapEnabled(true);
		
		/* Check number of arguments */
		switch (args.length){
			case 4:
				botNick = args[0];
	        	password = args[1];
	        	network = args[2];
	        	ircchannel = "##"+args[3];
	        	bot.setName(botNick);
	    	    bot.setLogin("Bot");
	        	try {
	    	    	bot.getCapHandlers().add(new SASLCapHandler(botNick, password));
	            	bot.connect(network);
	            } catch (Exception e){
	            	System.out.println("Error connecting to "+network);
	            }
	        	break;
			case 3:
				botNick = args[0];
				network = args[1];
	        	ircchannel = "##"+args[2];
	        	bot.setName(botNick);
	    	    bot.setLogin("Bot");
	    	    try {
	            	bot.connect(network);
	            } catch (Exception e){
	            	System.out.println("Error connecting to "+network);
	            }
	        	break;
			case 2:
				network = args[0];
	        	ircchannel = "##"+args[1];
	        	bot.setName("ExampleBot");
	    	    bot.setLogin("Bot");
	    	    try {
	    	    	bot.connect(network);
	            } catch (Exception e){
	            	System.out.println("Error connecting to "+network);
	            }
	        	break;
			default:
				System.out.println("Incorrect number of parameters. Required: " +
	        			"[botNick] [password] network channel.");
	        	System.exit(0);
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
	                	game = new Blackjack(event.getBot(), channel, commandChar);
	                	game.showGameStart();
	                } else {
	                    bot.sendMessage(channel,"A game is already running!");
	                }
	            } else if (msg.equals("texaspoker") || msg.equals("tp")) {
	            	if (game == null){
	                	game = new TexasPoker(event.getBot(), channel, commandChar);
	                	game.showGameStart();
	                } else {
	                    bot.sendMessage(channel,"A game is already running!");
	                }
	            } else if (msg.equals("endgame")) {
	                if (game == null){
	                    bot.sendMessage(channel, "No game is currently running!");
	                } else {
	                    game.endGame();
	                    game = null;
	                }
	            } else if (msg.equals("shutdown") || msg.equals("botquit")) {
	                if (game != null){
	                	game.endGame();
	                    game = null;
	                }
	                try {
	                	bot.quitServer();
	                } catch (Exception e){
	                	System.out.println("Exception caught during disconnection.");
	                }
	            }
            }
            if (game != null){
                game.processMessage(user, msg, origMsg);
            }
        }
    }
	
	@Override
    public void onConnect(ConnectEvent<PircBotX> event){
		bot.joinChannel(ircchannel);
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
