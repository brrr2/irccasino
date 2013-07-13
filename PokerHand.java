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

import java.util.Collections;

import org.pircbotx.Colors;

public class PokerHand extends Hand implements Comparable<PokerHand>{
	private int value;
	public PokerHand(){
		super();
		value = -1;
	}
	
	public int compareTo(PokerHand h){
		int value = this.getValue();
		int otherValue = h.getValue();
		
		if (value == otherValue){
			switch (value) {
				case 8: return this.get(0).getFaceValue() - h.get(0).getFaceValue();
				case 7: return this.get(0).getFaceValue() - h.get(0).getFaceValue();
				case 6:	
					if (this.get(0).getFaceValue() - h.get(0).getFaceValue() == 0){
						return this.get(3).getFaceValue() - h.get(3).getFaceValue();
					} else {
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					}
				case 5: 
					for (int ctr = 0; ctr < 5; ctr++){
						if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
							return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
						}
					}
					return 0;
				case 4:
					return this.get(0).getFaceValue() - h.get(0).getFaceValue();
				case 3:
					if (this.get(0).getFaceValue() - h.get(0).getFaceValue() == 0){
						for (int ctr = 3; ctr < 5; ctr++){
							if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
								return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
							}
						}
					} else {
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					}
					return 0;
				case 2: 
					if (this.get(0).getFaceValue() != h.get(0).getFaceValue()){
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					} else if (this.get(2).getFaceValue() != h.get(2).getFaceValue()){
						return this.get(2).getFaceValue() - h.get(2).getFaceValue();
					} else {
						return this.get(4).getFaceValue() - h.get(4).getFaceValue();
					}
				case 1:
					if (this.get(0).getFaceValue() != h.get(0).getFaceValue()){
						return this.get(0).getFaceValue() - h.get(0).getFaceValue();
					} else {
						for (int ctr = 2; ctr < 5; ctr++){
							if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
								return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
							}
						}
						return 0;
					}
				default:
					for (int ctr = 0; ctr < 5; ctr++){
						if (this.get(ctr).getFaceValue() != h.get(ctr).getFaceValue()){
							return this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
						}
					}
					return 0;
			}
		} else {
			return value - otherValue;
		}
	}
	
	public int getValue(){
		if (value == -1){
			value = calcValue();
		}
		return value;
	}
	public void resetValue(){
		value = -1;
	}
	
	public String getName(){
		switch (this.getValue()) {
			case 8: 
				if (get(0).getFace().equals("A")){
					return "Royal Flush";
				} else {
					return "Straight Flush";
				}
			case 7: return "Four of a Kind";
			case 6: return "Full House";
			case 5: return "Flush";
			case 4: return "Straight";
			case 3: return "Three of a Kind";
			case 2: return "Two Pairs";
			case 1: return "Pair";
			default: return get(0).toString()+Colors.NORMAL+" High";
		}
	}
	
	private int calcValue(){
		// Always check the hands in order of descending value
		if (hasStraightFlush(this)){	// Straight flush = 8
			return 8;
		} else if (hasFourOfAKind(this)){	// Four of a kind = 7
			return 7;
		} else if (hasFullHouse(this)){	// Full house = 6
			return 6;
		} else if (hasFlush(this)){	// Flush = 5
			return 5;
		} else if (hasStraight(this)){	// Straight = 4
			return 4;
		} else if (hasThreeOfAKind(this)){	// Three of a kind = 3
			return 3;
		} else if (hasTwoPair(this)){	// Two-pair = 2
			return 2;
		} else if (hasPair(this)){	// Pair = 1
			return 1;
		} else {	// High card = 0
			return 0;
		}
	}
	
	@Override
	public String toString(){
		return toString(5);
	}
	
	/*
	 * A smattering of methods to check for various poker card combinations.
	 * Methods require that hands be sorted in descending order.
	 */
	public static boolean hasPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-1; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			if (a.getFace() == b.getFace()){
				h.remove(a);
				h.remove(b);
				h.add(a, 0);
				h.add(b, 1);
				return true;
			}
		}
		return false;
	}
	public static boolean hasTwoPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-3; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			if (a.getFace() == b.getFace()){
				h.remove(a);
				h.remove(b);
				if (hasPair(h)){
					h.add(a, 0);
					h.add(b, 1);
					return true;
				} else {
					h.add(a, ctr);
					h.add(b, ctr+1);
					return false;
				}
			}
		}
		return false;
	}
	public static boolean hasThreeOfAKind(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			if (a.getFace() == b.getFace() && b.getFace() == c.getFace()){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				h.add(a, 0);
				h.add(b, 1);
				h.add(b, 2);
				return true;
			}
		}
		return false;
	}
	public static boolean hasStraight(Hand h){
		Card c;
		/* Create an boolean array to determine which face cards exist in the hand.
		 * An extra index is added at the beginning for the value duality of aces.
		 */
		boolean[] cardValues = new boolean[CardDeck.faces.length+1];
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			if (c.getFace().equals("A")){
				cardValues[0] = true;
			}
			cardValues[c.getFaceValue()+1] = true;
		}
		// Determine if any 5 sequential cards exist
		for (int ctr = cardValues.length-1; ctr >= 4; ctr--){
			if (cardValues[ctr] && cardValues[ctr-1] && cardValues[ctr-2] && 
				cardValues[ctr-3] && cardValues[ctr-4]){
				// Move the sequence in descending order to the beginning of the hand
				for (int ctr2 = 0; ctr2 < 5; ctr2++){
					for (int ctr3=0; ctr3<h.getSize(); ctr3++){
						c = h.get(ctr3);
						if ((ctr-ctr2 == 0 && c.getFace().equals("A")) || c.getFaceValue()+1 == ctr-ctr2){
							h.remove(c);
							h.add(c,ctr2);
							break;
						}
					}
				}
				return true;
			}
		}
		return false;
	}
	public static boolean hasFlush(Hand h){
		int[] suitCount = new int[CardDeck.suits.length];
		Card c;
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			suitCount[c.getSuitValue()]++;
		}
		for (int ctr = 0; ctr < suitCount.length; ctr++){
			if (suitCount[ctr] >= 5){
				int count = 0;
				for (int ctr3=0; ctr3<h.getSize(); ctr3++){
					c = h.get(ctr3);
					if (c.getSuitValue() == ctr){
						h.remove(c);
						h.add(c,count++);
					}
				}
				return true;
			}
		}
		return false;
	}
	public static boolean hasFullHouse(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			if (a.getFace() == b.getFace() && a.getFace() == c.getFace()){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				boolean hp = hasPair(h);
				if (hp){
					h.add(a,0);
					h.add(b,1);
					h.add(c,2);
					return true;
				} else {
					h.add(a,ctr);
					h.add(b,ctr+1);
					h.add(c,ctr+2);
					return false;
				}
			}
		}
		return false;
	}
	public static boolean hasFourOfAKind(Hand h){
		Card a,b,c,d;
		for (int ctr = 0; ctr < h.getSize()-3; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			d = h.get(ctr+3);
			if (a.getFace() == b.getFace() && b.getFace() == c.getFace() 
					&& c.getFace() == d.getFace()){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				h.remove(d);
				h.add(a,0);
				h.add(b,1);
				h.add(c,2);
				h.add(d,2);
				return true;
			}
		}
		return false;
	}

	public static boolean hasStraightFlush(Hand h){
		int[] suitCount = new int[CardDeck.suits.length];
		Hand nonFlushCards = new Hand();
		Card c;
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			suitCount[c.getSuitValue()]++;
		}
		// Reorganizes the cards to reveal the first straight flush
		// that is found.
		for (int ctr = 0; ctr < CardDeck.suits.length; ctr++){
			if (suitCount[ctr] >= 5){
				for (int ctr2 = 0; ctr2 < h.getSize(); ctr2++){
					if (h.get(ctr2).getSuitValue() != ctr){
						nonFlushCards.add(h.get(ctr2));
						h.remove(ctr2--);
					}
				}
				if (hasStraight(h)){
					h.addAll(nonFlushCards);
					nonFlushCards.clear();
					return true;
				} else {
					h.addAll(nonFlushCards);
					nonFlushCards.clear();
				}
			}
		}
		Collections.sort(h.getAllCards());
		Collections.reverse(h.getAllCards());
		return false;
	}
}
