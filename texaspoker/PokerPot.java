/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

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

package irccasino.texaspoker;

import java.util.ArrayList;

/**
 * A pot class to handle bets and payouts in Texas Hold'em Poker.
 * @author Yizhe Shen
 */
public class PokerPot {
    private ArrayList<PokerPlayer> players;
    private ArrayList<PokerPlayer> donors;
    private int total;

    public PokerPot(){
        total = 0;
        players = new ArrayList<PokerPlayer>();
        donors = new ArrayList<PokerPlayer>();
    }

    public int getTotal(){
        return total;
    }
    public void add(int amount){
        total += amount;
    }
    public void addPlayer(PokerPlayer p){
        players.add(p);
    }
    public void removePlayer(PokerPlayer p){
        players.remove(p);
    }
    public void addDonor(PokerPlayer p) {
        donors.add(p);
    }
    public void removeDonor(PokerPlayer p) {
        donors.remove(p);
    }
    public PokerPlayer getPlayer(int c){
        return players.get(c);
    }
    public ArrayList<PokerPlayer> getPlayers(){
        return players;
    }
    public PokerPlayer getDonor(int c) {
        return donors.get(c);
    }
    public ArrayList<PokerPlayer> getDonors() {
        return donors;
    }
    public boolean hasPlayer(PokerPlayer p){
        return players.contains(p);
    }
    public boolean hasDonor(PokerPlayer p) {
        return donors.contains(p);
    }
    public int getNumPlayers(){
        return players.size();
    }
    public int getNumDonors() {
        return donors.size();
    }
}