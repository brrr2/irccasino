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

import java.util.*;
import org.pircbotx.*;

public class BlackjackPlayer extends Player{
	protected int currentIndex;
	protected ArrayList<Hand> hands;
	protected int initialBet;
	
	/**
	 * Class constructor for subclass of Player
	 * 
	 * @param u		IRC user object
	 * @param d		Whether or not player is dealer
	 */
    public BlackjackPlayer(User u, boolean d){
        super(u,d);
        hands = new ArrayList<Hand>();
        currentIndex = 0;
        initialBet = 0;
    }

    /* Blackjack-specific card/hand manipulation methods */
    public void addHand(){
    	hands.add(new Hand());
    }
    public Hand getHand(int num){
    	return hands.get(num);
    }
    public int getCurrentIndex(){
    	return currentIndex;
    }
    public void resetCurrentIndex(){
    	currentIndex = 0;
    }
    public Hand getCurrentHand(){
    	return hands.get(currentIndex);
    }
    public Hand getNextHand(){
    	return getHand(++currentIndex);
    }
    public int getNumberHands(){
    	return hands.size();
    }
    public boolean hasHands(){
        return hands.size() > 0;
    }
    public void resetHands(){
    	hands.clear();
    }
    
    /* Betting methods */
    public void setInitialBet(int b){
    	initialBet = b;
    }
    public int getInitialBet(){
    	return initialBet;
    }
    public boolean hasInitialBet(){
    	return initialBet > 0;
    }
    public void clearInitialBet(){
    	initialBet = 0;
    }
    
    /* Methods related to splitting hands */
    public boolean hasSplit(){
    	return hands.size() > 1;
    }
    public void splitHand(){
    	Hand tHand = new Hand();
    	Hand cHand = getCurrentHand();
    	tHand.add(cHand.get(1));
    	cHand.remove(1);
    	
    	hands.add(currentIndex+1, tHand);
    }
    
}
