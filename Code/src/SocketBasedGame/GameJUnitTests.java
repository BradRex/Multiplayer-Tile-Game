package SocketBasedGame;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GameJUnitTests {

    private Game game;
    private PlayerMark mark = PlayerMark.RED;
    private PlayerMark opMark = PlayerMark.GREEN;

    /*Note:
     *The Double move influence card cannot be tested effectively as this is handled inside the GameService class.
     *The GameService class just simply doesn't call the nextPlayer() method to move the turn on to the next player.
     *Also, throughout the program, x is a row and y is a column.*/

    //This test places a red tile in the top left corner and demonstrates the use of the Freedom influence card.
    @Test
    public void testFreedom(){
        game = new Game();
        game.setHadFirstTrue();

        //Place a tile on the board in the top left for red.
        int x = 0;
        int y = 0;
        game.setTile(0, 0, mark.ordinal());


        /*Place a tile which is not adjacent, WITHOUT freedom.
         *Should return false as it has no adjacent tiles with its mark.*/
        int x1 = 0;
        int y1 = 2;
        assertEquals(false, game.makeMove(InfluenceCard.NONE.toString(), x1, y1, mark.ordinal()));

        /*Place a tile which is not adjacent, WITH freedom.
         *Should return true as Freedom ignores the adjacency rule.*/
        assertEquals(true, game.makeMove(InfluenceCard.FREEDOM.toString(), x1, y1, mark.ordinal()));

        /*Place a tile which is not adjacent, WITH freedom, but on a non-empty space.
         *Should return false as Freedom still requires an empty tile.*/
        int x2 = 5;
        int y2 = 5;
        game.setTile(x2, y2, mark.ordinal());
        assertEquals(false, game.makeMove(InfluenceCard.FREEDOM.toString(), x2, y2, mark.ordinal()));
    }

    //This test places a red tile in the top left corner and demonstrates the use of the Replacement influence card.
    @Test
    public void testReplacement(){
        game = new Game();
        game.setHadFirstTrue();

        //Place a tile on the board in the top left for red.
        int x = 0;
        int y = 0;
        game.setTile(x, y, mark.ordinal());

        //Place a tile of a different mark adjacent to red's tile.
        int xG = 0;
        int yG = 1;
        game.setTile(xG, yG, opMark.ordinal());

        /*Place a tile which is adjacent but is not empty, WITHOUT replacement.
         *Should return false as the tile not empty.*/
        assertEquals(false, game.makeMove(InfluenceCard.NONE.toString(), xG, yG, mark.ordinal()));

        /*Place a tile which is adjacent but is not empty, WITH replacement.
         *Should return true as Replacement ignores if the tile is empty.*/
        assertEquals(true, game.makeMove(InfluenceCard.REPLACEMENT.toString(), xG, yG, mark.ordinal()));

        /*Place a tile which is not adjacent but is not empty, WITH replacement.
        *Should return false as Replacement still requires adjacent tiles with the same mark*/
        int x1 = 5;
        int y1 = 5;
        assertEquals(false, game.makeMove(InfluenceCard.REPLACEMENT.toString(), x1, y1, mark.ordinal()));

        /*Place a tile which is adjacent and not empty but has the same mark as the move, WITH replacement.
         *Should return false as Replacement cannot replace tiles with the same mark as the move.*/
        game.setTile(x+1, y, mark.ordinal());
        assertEquals(false, game.makeMove(InfluenceCard.REPLACEMENT.toString(), x+1, y, mark.ordinal()));
    }

    //This test checks if a placed tile has any adjacent tiles with the same mark. Tests the checkAdjacent method.
    @Test
    public void testCheckAdjacent(){
        game = new Game();
        game.setHadFirstTrue();

        //Place a tile on the board in the top left for red.
        int x = 0;
        int y = 0;
        game.setTile(0, 0, mark.ordinal());

        /*Checks adjacency of a tile which DOESN'T have an adjacent tile with the same mark. Adj tiles are empty.
         *Should return false as the tile (x, y+2) only has empty tiles around it.*/
        assertEquals(false, game.useCheckAdjacent(x, y+2, mark.ordinal()));

        /*Checks adjacency of a tile which DOES have an adjacent tile with the same mark.
         *Should return true as the tile (x, y+1) has one adjacent tile with its mark at (x, y)*/
        assertEquals(true, game.useCheckAdjacent(x, y+1, mark.ordinal()));

        //Place a tile which is going to be adjacent to the red tile but is a different mark.
        game.setTile(x, y+1, opMark.ordinal());

        /*Checks adjacency of a tile which DOESN'T have an adjacent tile with the same mark. Adj tiles not same mark.
         *Should return false as even if a tile isn't empty it still has to have the same mark.*/
        assertEquals(false, game.useCheckAdjacent(x, y+2, mark.ordinal()));
    }
}