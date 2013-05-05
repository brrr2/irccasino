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
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.managers.*;

public class ExampleBot extends ListenerAdapter {
	
	public static PircBotX bot;
	public static ThreadedListenerManager<PircBotX> manager;
	public static CardGame game;
	    
	public static void main(String[] args) throws Exception {
	    //Create Listener Manager to use
	    manager = new ThreadedListenerManager<PircBotX>();
	    manager.addListener(new ExampleBot());
	    
	    bot = new PircBotX();
	    bot.setListenerManager(manager);
	    bot.setName("ExampleBot");
	    bot.setLogin("ExampleBot");
	    bot.setVerbose(true);
	    bot.setAutoNickChange(true);
	    bot.setCapEnabled(true);
	
	    bot.connect("chat.freenode.net");
	    bot.joinChannel("##ExampleChannel");
	}
	
	@Override
    public void onMessage (MessageEvent event){
        String msg = event.getMessage().toLowerCase();
        Channel channel = event.getChannel();
        User user = event.getUser();
        if (msg.charAt(0) == '.' && channel.isOp(user)){
            if (msg.equals(".blackjack") || msg.equals(".bj")) {
                if (game == null){
                	game = new Blackjack(event.getBot(), channel);
                	game.showGameStart();
                    manager.addListener(game);
                } else {
                    bot.sendMessage(channel,"A game is already running!");
                }
            } else if (msg.equals(".endgame")) {
                if (game == null){
                    bot.sendMessage(channel, "No game is currently running!");
                } else {
                    game.showGameEnd();
                    game.endGame();
                    manager.removeListener(game);
                    game = null;
                }
            } else if (msg.equals(".shutdown") || msg.equals(".botquit")) {
                if (game != null){
                	game.showGameEnd();
                	game.endGame();
                    manager.removeListener(game);
                    game = null;
                }
                bot.quitServer();
            }
        }
    }
    
}
