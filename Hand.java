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

public class Hand {
	protected ArrayList<Card> cards;
	protected int bet;
	
	/**
	 * Class constructor for a hand of cards
	 */
	public Hand(){
		cards = new ArrayList<Card>();
		bet = 0;
	}
	
	/* Accessor methods */
	public void add(Card card){
		cards.add(card);
	}
	public void remove(int index){
		cards.remove(index);
	}
	public Card get(int index){
		return cards.get(index);
	}
	public ArrayList<Card> getAllCards(){
		return cards;
	}
	public void clear(){
		cards.clear();
	}
	public int getSize(){
		return cards.size();
	}
	public boolean hasHit(){
		return getSize() != 2;
	}
	
    /* Blackjack specific betting, may get moved to a subclass */
	public void setBet(int amount){
    	bet = amount;
    }
    public void addBet(int amount){
        bet += amount;
    }
    public int getBet(){
        return bet;
    }
    public void clearBet(){
        bet = 0;
    }
	
}
