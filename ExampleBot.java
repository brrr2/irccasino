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

import org.pircbotx.*;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.managers.*;

public class ExampleBot extends ListenerAdapter<PircBotX> {
	
	public static PircBotX bot;
	public static ThreadedListenerManager<PircBotX> manager;
	public static CardGame game;
	public static String botNick, password, network, channel;
	public static final char commandChar = '.';
	    
	public static void main(String[] args) throws Exception {
		 //Create Listener Manager to use
	    manager = new ThreadedListenerManager<PircBotX>();
	    manager.addListener(new ExampleBot());
	    
	    bot = new PircBotX();
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
	        	channel = "##"+args[3];
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
	        	channel = "##"+args[2];
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
	        	channel = "##"+args[1];
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
        String msg = event.getMessage().toLowerCase();
        Channel channel = event.getChannel();
        User user = event.getUser();
        if (msg.length() > 1 && msg.charAt(0) == commandChar && channel.isOp(user)){
        	msg = msg.substring(1);
            if (msg.equals("blackjack") || msg.equals("bj")) {
                if (game == null){
                	game = new Blackjack(event.getBot(), channel, commandChar);
                	game.showGameStart();
                    manager.addListener(game);
                } else {
                    bot.sendMessage(channel,"A game is already running!");
                }
            } else if (msg.equals("texaspoker") || msg.equals("tp")) {
            	if (game == null){
                	game = new TexasPoker(event.getBot(), channel, commandChar);
                	game.showGameStart();
                    manager.addListener(game);
                } else {
                    bot.sendMessage(channel,"A game is already running!");
                }
            } else if (msg.equals("endgame")) {
                if (game == null){
                    bot.sendMessage(channel, "No game is currently running!");
                } else {
                    game.endGame();
                    manager.removeListener(game);
                    game = null;
                }
            } else if (msg.equals("shutdown") || msg.equals("botquit")) {
                if (game != null){
                	game.endGame();
                    manager.removeListener(game);
                    game = null;
                }
                try {
                	bot.quitServer();
                } catch (Exception e){
                	System.out.println("Exception caught during disconnection.");
                }
            }
        }
    }
	
	@Override
    public void onConnect (ConnectEvent<PircBotX> event){
		bot.joinChannel(channel);
    }
}
