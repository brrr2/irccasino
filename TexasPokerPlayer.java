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

public class TexasPokerPlayer extends Player {
	private Hand hand;
	private int initialBet;
	private boolean fold;
	
	public TexasPokerPlayer(String nick, String hostmask, boolean dealer){
        super(nick, hostmask, dealer);
        hand = new Hand();
        fold = false;
        initialBet = 0;
    }

	public boolean hasHand(){
		return (hand.getSize() > 0);
	}
	public Hand getHand(){
		return hand;
	}
	public void resetHand(){
		hand.clear();
	}
	
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
    
    public void setFold(boolean b){
    	fold = b;
    }
    public boolean hasFolded(){
    	return fold;
    }
}
