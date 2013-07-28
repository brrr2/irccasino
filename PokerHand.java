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

public class PokerHand extends Hand implements Comparable<PokerHand>{
	private int value;
	public static String[] handNames = {"High Card","Pair","Two Pairs",
                                        "Three of a Kind","Straight",
                                        "Flush","Full House","Four of a Kind",
                                        "Straight Flush","Royal Flush"};
    
    /**
     * Creates a new PokerHand.
     * The same as a Hand with extra methods for hand comparisons.
     */
    public PokerHand(){
		super();
		value = -1;
	}
	
    /*
     * Comparisons require that both hands be sorted in descending order and
     * that the cards representing the hand value be at the beginning of the
     * hand. This means that getValue() must be called before making
     * any comparisons.
     */
    @Override
	public int compareTo(PokerHand h){
		int thisValue = this.getValue();
		int otherValue = h.getValue();
		
        // Check if hands have same value
		if (thisValue == otherValue){
            // Calculate comparisons for each card
            int[] comps = new int[5];
            for (int ctr = 0; ctr < 5; ctr++){
                comps[ctr] = this.get(ctr).getFaceValue() - h.get(ctr).getFaceValue();
            }
            
			switch (value) {
                // Straight Flush and Straight: check top card of straight
				case 9: case 8: case 4:
                    return comps[0];
                // 4 of a Kind: compare 4 of a kind, then kicker
				case 7: 
                    if (comps[0] == 0){
                        return comps[4];
                    }
                    return comps[0];
                // Full House: check triplet, then pair
				case 6:	
					if (comps[0] == 0){
						return comps[3];
					}
					return comps[0];
				// Flush: check highest non-common card
                case 5: 
					for (int ctr = 0; ctr < 5; ctr++){
						if (comps[ctr] != 0){
							return comps[ctr];
						}
					}
					return 0;
                // Three of a Kind: check triplet, then check kickers
				case 3:
					if (comps[0] == 0){
						for (int ctr = 3; ctr < 5; ctr++){
							if (comps[ctr] != 0){
								return comps[ctr];
							}
						}
                        return 0;
					}
					return comps[0];
                // Two pair: check each pair, then check kicker
				case 2: 
					if (comps[0] != 0){
						return comps[0];
					} else if (comps[2] != 0){
						return comps[2];
					}
                    return comps[4];
                // Pair: check pair, then check highest non-common card
				case 1:
					if (comps[0] == 0){
						for (int ctr = 2; ctr < 5; ctr++){
							if (comps[ctr] != 0){
								return comps[ctr];
							}
						}
						return 0;
					}
                    return comps[0];
                // High Card: check highest non-common card
				default:
					for (int ctr = 0; ctr < 5; ctr++){
						if (comps[ctr] != 0){
							return comps[ctr];
						}
					}
					return 0;
			}
		}
		return thisValue - otherValue;
	}

    public int getValue(){
		if (value == -1){
			value = calcValue(this);
		}
		return value;
	}
	public void resetValue(){
		value = -1;
	}
	
    /**
     * Name of the hand.
     * @return the name of the hand based on value.
     */
    public String getName(){
		return handNames[this.getValue()];
	}
	@Override
	public String toString(){
        switch (this.getValue()) {
            case 8: return toString(0,5);
            case 7: return toString(0,4)+"/"+toString(4,5);
            case 6: return toString(0,5);
            case 5: return toString(0,5);
            case 4: return toString(0,5);
            case 3: return toString(0,3)+"/"+toString(3,5);
            case 2: return toString(0,4)+"/"+toString(4,5);
            case 1: return toString(0,2)+"/"+toString(2,5);
            default: return toString(0,5);
        }
	}

	/*
	 * A collection of methods to check for various poker card combinations.
	 * Methods require that hands be sorted in descending order.
	 */
    private static int calcValue(PokerHand h){
		// Always check the hands in order of descending value
		if (hasStraightFlush(h)){	
			if (h.get(0).getFace().equals("A")){
                return 9; // Royal flush = 9
            }
            return 8;   // Straight flush = 8
		} else if (hasFourOfAKind(h)){	// Four of a kind = 7
			return 7;
		} else if (hasFullHouse(h)){	// Full house = 6
			return 6;
		} else if (hasFlush(h)){	// Flush = 5
			return 5;
		} else if (hasStraight(h)){	// Straight = 4
			return 4;
		} else if (hasThreeOfAKind(h)){	// Three of a kind = 3
			return 3;
		} else if (hasTwoPair(h)){	// Two-pair = 2
			return 2;
		} else if (hasPair(h)){	// Pair = 1
			return 1;
		} else {	// High card = 0
			return 0;
		}
	}
	private static boolean hasPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-1; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			if (a.getFace().equals(b.getFace())){
				h.remove(a);
				h.remove(b);
				h.add(a, 0);
				h.add(b, 1);
				return true;
			}
		}
		return false;
	}
	private static boolean hasTwoPair(Hand h){
		Card a,b;
		for (int ctr = 0; ctr < h.getSize()-3; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			if (a.getFace().equals(b.getFace())){
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
	private static boolean hasThreeOfAKind(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			if (a.getFace().equals(b.getFace()) && b.getFace().equals(c.getFace())){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				h.add(a, 0);
				h.add(b, 1);
				h.add(c, 2);
				return true;
			}
		}
		return false;
	}
	private static boolean hasStraight(Hand h){
		/* Create a boolean array to determine which face cards exist in the hand.
		 * An extra index is added at the beginning for the value duality of aces.
		 */
		boolean[] cardValues = new boolean[CardDeck.faces.length+1];
        Card c;
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			if (c.getFace().equals("A")){
				cardValues[0] = true;
			}
			cardValues[c.getFaceValue()+1] = true;
		}
		// Determine if any sequence of 5 consecutive cards exist
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
	private static boolean hasFlush(Hand h){
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
	private static boolean hasFullHouse(Hand h){
		Card a,b,c;
		for (int ctr = 0; ctr < h.getSize()-2; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			if (a.getFace().equals(b.getFace()) && a.getFace().equals(c.getFace())){
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
	private static boolean hasFourOfAKind(Hand h){
		Card a,b,c,d;
		for (int ctr = 0; ctr < h.getSize()-3; ctr++){
			a = h.get(ctr);
			b = h.get(ctr+1);
			c = h.get(ctr+2);
			d = h.get(ctr+3);
			if (a.getFace().equals(b.getFace()) && b.getFace().equals(c.getFace()) 
					&& c.getFace().equals(d.getFace())){
				h.remove(a);
				h.remove(b);
				h.remove(c);
				h.remove(d);
				h.add(a,0);
				h.add(b,1);
				h.add(c,2);
				h.add(d,3);
				return true;
			}
		}
		return false;
	}
	private static boolean hasStraightFlush(Hand h){
		int[] suitCount = new int[CardDeck.suits.length];
		Hand nonFlushCards = new Hand();
		Card c;
		for (int ctr = 0; ctr < h.getSize(); ctr++){
			c = h.get(ctr);
			suitCount[c.getSuitValue()]++;
		}
		/* Reorganizes the cards to reveal the first suit that has 
         * a straight flush. */
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
        // Re-sorts the hand in descending order if no straight flush is found
		Collections.sort(h.getAllCards());
		Collections.reverse(h.getAllCards());
		return false;
	}
}
