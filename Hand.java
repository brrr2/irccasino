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

import java.util.ArrayList;
import org.pircbotx.Colors;

public class Hand {
	protected ArrayList<Card> cards;
	
	/**
	 * Class constructor for a hand of cards
	 */
	public Hand(){
		cards = new ArrayList<Card>();
	}
	
	/* Accessor methods */
	public void add(Card card){
		cards.add(card);
	}
	public void add(Card card, int index){
		cards.add(index, card);
	}
	public void addAll(ArrayList<Card> cardList){
		if (cardList.size() > 0){
			cards.addAll(cardList);
		}
	}
	public void addAll(Hand hand){
		if (hand.getSize() > 0){
			cards.addAll(hand.getAllCards());
		}
	}
	public void remove(int index){
		cards.remove(index);
	}
	public void remove(Card c){
		cards.remove(c);
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
	/* Returns a subset specified by the indices (excluding end) as a new hand */
	public Hand subHand(int start, int end){
		Hand h = new Hand();
		for (int ctr = start; ctr < end; ctr++){
			h.add(this.get(ctr));
		}
		return h;
	}
	
	@Override
    public String toString(){
        return toString(getSize());
    }
	public String toString(int num){
        return toString(0,num);
	}
    public String toString(int start, int end){
		String outStr= "";
		int slimit = Math.max(0, start);
        int elimit = Math.min(this.getSize(), end);
        for (int ctr=slimit; ctr<elimit; ctr++){
            outStr += cards.get(ctr)+" ";
        }
        return outStr.substring(0, outStr.length()-1)+Colors.NORMAL;
	}
}
