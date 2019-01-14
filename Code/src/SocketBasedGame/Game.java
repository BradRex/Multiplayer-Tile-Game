package SocketBasedGame;

import java.util.ArrayList;
import java.util.List;

/*This class represents the game that the clients interact with via the GameService.
 *As well as maintaining/changing the state, it has methods for triggering updates for all players.*/
public class Game {

    //Constants for the game boards dimensions.
    public static final int ROWS = 6;
    public static final int COLUMNS = 10;
    //A list of all GameServices (Players) playing the current game.
    private List<GameService> players;
    //An int array to hold the marks at each position on the board.
    private int[][] gameBoard;
    /*A boolean array where each element corresponds to a player, indexed by the players mark.
     *E.g. PlayerMark.RED has ordinal value 1 so isBlocked[1] will indicate if the Red player is blocked (true)*/
    private boolean[] isBlocked;
    /*A boolean array to represent if a player has had their first mark placed. Allows GameService to place the initial
     *mark without checking for matching adjacent marks (which there wouldn't be if its the first move).*/
    private boolean[] hadFirst;
    //A boolean flag to indicate if the game has finished.
    private boolean finished;
    //A PlayerMark to hold the mark of the player who's turn it currently is. Used to handle concurrent access.
    private PlayerMark playerMarkTurn;
    //Tracks the number of players in the game. A primary use is to ensure a game can't start with only 1 player.
    private int playerCount;

    //Game constructor to initialise data members.
    public Game() {
        players = new ArrayList<>();
        gameBoard = new int[ROWS][COLUMNS];
        isBlocked = new boolean[]{true, false, false, false, false, false};
        hadFirst = new boolean[]{false, false, false, false, false, false};
        finished = false;
        playerMarkTurn = PlayerMark.NONE;
        playerCount = 0;
    }

    //Adds a player (GameService) to the players list, increases the player count and starts the game if needed.
    public void addPlayer(GameService gameService) {
        players.add(gameService);
        playerCount += 1;
        if (playerCount == 2) {
            //This executes the startGame() method is a separate thread to allow it to wait 10 seconds for more players.
            new Thread(() -> {
                try{
                    Thread.sleep(10000);
                    startGame();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    //This sets the player turn to RED as RED is always first, then informs all players of who's turn it is.
    private void startGame() {
        playerMarkTurn = PlayerMark.RED;
        informPlayersOfTurn();
    }

    //This instructs each player (GameService) currently connected to inform their clients of who's turn it is.
    private void informPlayersOfTurn() {
        for (GameService gameService : players) {
            gameService.informClientOfTurn(playerMarkTurn);
        }
    }

    /*Synchronised here means that this method cant be called at the same time by two threads. Stops new
     *players from filling a space which a player clicked on. Either the new player or current player gets it.
     *This method checks if a move is valid with a given influence card and the players mark.*/
    public synchronized boolean makeMove(String card, int x, int y, int playerMark) {
        //This is the influence card being used in this move, InfluenceCard.
        InfluenceCard curCard = InfluenceCard.valueOf(card);

        /*If the move is using no card or the double card, then only checking if the space is free and is adjacent
         *to another tile with the same mark as the one being placed.*/
        if (curCard == InfluenceCard.NONE || curCard == InfluenceCard.DOUBLE) {
            //This checks if the tile not occupied.
            if (gameBoard[x][y] == PlayerMark.NONE.ordinal()) {
                //This allows for a players first, random tile to be placed as they won't have an adjacent tile yet.
                if (!hadFirst[playerMark]) {
                    hadFirst[playerMark] = true;
                    gameBoard[x][y] = playerMark;
                    return true;
                }
                /*If the tile is free and its not the player first move, then check that there is an adjacent tile with
                 *with the same mark as the one being placed.*/
                return checkAdjacent(x, y, playerMark);
            } else {
                //The tile was occupied, so return false.
                return false;
            }
        } else if (curCard == InfluenceCard.REPLACEMENT) {
            /*The replacement card is being used, so there is no need to check if the space is free. Only need to check
             *that there is an adjacent tile with the same mark and the tile being replaced is not one of their own.
             *Note: Can replace a free tile, but that is the players choice.*/
            if (gameBoard[x][y] == playerMark) {
                return false;
            } else {
                return checkAdjacent(x, y, playerMark);
            }
        } else if (curCard == InfluenceCard.FREEDOM) {
            /*The freedom card is being used, so there is no need to check the tile is adjacent to one with the same
             *mark. Only need to check the tile is not occupied.*/
            if (gameBoard[x][y] == PlayerMark.NONE.ordinal()) {
                gameBoard[x][y] = playerMark;
                return true;
            } else {
                return false;
            }
        } else {
            //An unrecognised card was used, so by default don't allow it. This should never be reached.
            return false;
        }
    }

    /*Checks if a tile has a tile adjacent to it with the same mark. If so, it places the tile and returns true.
     *otherwise, returns false to indicate it wasn't placed.*/
    private boolean checkAdjacent(int x, int y, int playerMark) {
        boolean valid = false;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                try {
                    //Attempts to check an adjacent tile.
                    valid = gameBoard[x + i][y + j] == playerMark;
                    if (valid) {
                        gameBoard[x][y] = playerMark;
                        return true;
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    /*Catching an array index exception allows for the general method to be applied regardless of the
                     *tile position, otherwise, checks for corner and edge tiles would be needed.*/
                }
            }
        }
        return false;
    }

    /*Get the winner and the scores, then pass that information to the client by calling each players end method.
     *Set finished flag to true to flag the game has ended.*/
    public void endGame() {
        String scores = getScoresAndWinner();
        for (GameService gameService : players) {
            gameService.end(scores);
        }
        System.out.println("WINNER IS: " + scores);
        finished = true;
        System.out.println("GAME OVER.");
    }

    /*This method constructs a string of the format PlayerMark Score Score, where the player mark is the mark of the
     *player who has the highest score and the scores are the scores of the players in order of joining.*/
    private String getScoresAndWinner() {
        //Get the scores of each player by counting their marked tiles.
        int[] scores = new int[playerCount];
        for (int[] row : gameBoard) {
            for (int position : row) {
                if (position != 0) {
                    scores[position - 1]++;
                }
            }
        }

        //Track the highest score value and who has that score.
        int highestScore = 0;
        String curWinner = "";

        //Represents the string being constructed for output from this method.
        String scoresString = " ";

        //Check for the highest score and the corresponding player and construct the string to be returned.
        for (int i = 0; i < scores.length; i++) {
            scoresString += scores[i] + " ";
            if (scores[i] >= highestScore) {
                highestScore = scores[i];
                curWinner = PlayerMark.values()[1 + i].toString();
            }
        }

        return curWinner + scoresString;
    }

    //Sets a player as blocked by changing their blocked value to true in the isBlocked array.
    public void setBlocked(int i) {
        isBlocked[i] = true;
    }

    //Check if a player is blocked and mark them as blocked if they are.
    public void checkBlocked() {
        for (GameService player : players) {
            if (player.isBlocked()) {
                //This player is already blocked, so no need to perform further checks.
            } else if (player.availableCards()[InfluenceCard.REPLACEMENT.ordinal()]) {
                //A player can never be blocked if they have a replacement card.
            } else if (boardFull()) {
                /*If the board is full and the player doesn't have a replacement card, then neither a double nor a
                 *freedom can unblock the player so the player is are blocked.*/
                System.out.println(player.getPlayerMark() + " is blocked");
                isBlocked[player.getPlayerMark().ordinal()] = true;
                player.setBlocked();
            } else if (player.availableCards()[InfluenceCard.FREEDOM.ordinal()]) {
                /*If the board is not full (implied by this point) and the player has a freedom, then the player is not
                 *blocked.*/
            } else {
                //Check if a player has an empty space adjacent to one of their tiles.
                PlayerMark mark = player.getPlayerMark();

                //Allows the check to exit the loops as soon as an empty adjacent space is found
                boolean hasSpace = false;

                /*Check each tile for a tile marked by the current player. Then check if there is an empty tile adjacent
                 *to it. Break out of the loops if at least one is found.*/
                for (int row = 0; row < Game.ROWS; row++) {
                    if (hasSpace) {
                        break;
                    }
                    for (int column = 0; column < Game.COLUMNS; column++) {
                        if (hasSpace) {
                            break;
                        }
                        if (gameBoard[row][column] == mark.ordinal()) {
                            //Check adjacent tiles for an empty one.
                            for (int i = -1; i < 2; i++) {
                                if (hasSpace) {
                                    break;
                                }
                                for (int j = -1; j < 2; j++) {
                                    try {
                                        if (gameBoard[row + i][column + j] == PlayerMark.NONE.ordinal()) {
                                            hasSpace = true;
                                            break;
                                        }
                                    } catch (ArrayIndexOutOfBoundsException e) {
                                        /*Catching an array index exception allows for the general method to be applied
                                         *regardless of the tile position, otherwise, checks for corner and edge tiles
                                         *would be needed.*/
                                    }
                                }
                            }
                        }
                    }
                }

                //If hasSpace is false at this point, then the player is blocked.
                if (!hasSpace) {
                    System.out.println(player.getPlayerMark() + " is blocked");
                    isBlocked[player.getPlayerMark().ordinal()] = true;
                    player.setBlocked();
                }
            }
        }
    }

    //Checks if all the players currently in the game are blocked.
    private boolean allBlocked() {
        //Start at one because index 0 is for the NONE player who is always blocked.
        for (int i = 1; i <= playerCount; i++) {
            if (!isBlocked[i]) {
                //If at least one player isn't blocked, then they are not all blocked.
                return false;
            }
        }

        //Everyone was blocked.
        return true;
    }

    //Checks if there are any empty tiles left on the board.
    public boolean boardFull() {
        for (int[] row : gameBoard) {
            for (int tile : row) {
                if (tile == PlayerMark.NONE.ordinal()) {
                    return false;
                }
            }
        }

        return true;
    }

    //Checks if the game is over by checking if all players are blocked.
    public boolean isGameOver() {
        checkBlocked();
        if (allBlocked()) {
            return true;
        } else {
            return false;
        }
    }

    //Checks if the game has been flagged as finished.
    public boolean isFinished() {
        return finished;
    }

    //Flattens the game board array into a single string to be sent to the players to update their clients.
    public void sendBoard() {
        //Constructs flattened array as a string.
        String board = "";
        for (int[] row : gameBoard) {
            for (int pos : row) {
                board += pos + " ";
            }
        }

        //Sends the game board to all players.
        for (GameService gameService : players) {
            gameService.updateBoard(board);
        }
    }

    //Returns the mark of the player who's turn it currently is.
    public PlayerMark getPlayerMarkTurn() {
        return playerMarkTurn;
    }

    //Determines who the next player should be by skipping the next player if that player is blocked.
    public void nextPlayer() {
        //The current player who just made a move.
        PlayerMark player = playerMarkTurn;

        //The next player to have a turn. If the current player is the last one, then it moves back to the first player.
        PlayerMark nextPlayer = player.ordinal() == playerCount ? PlayerMark.values()[1] : PlayerMark.values()[playerMarkTurn.ordinal() + 1];

        //Check the next player isn't blocked and return if they are not. Otherwise, move on another player.
        while (nextPlayer.ordinal() != player.ordinal()) {
            if (isBlocked[nextPlayer.ordinal()]) {
                nextPlayer = nextPlayer.ordinal() == playerCount ? PlayerMark.values()[1] : PlayerMark.values()[nextPlayer.ordinal() + 1];
            } else {
                playerMarkTurn = nextPlayer;
                informPlayersOfTurn();
                return;
            }
        }

        /*If this point is reached then all players next in line are blocked.
         *Now check if the current player is blocked.*/
        if (isBlocked[player.ordinal()]) {
            //All players are blocked. Call endGame().
            endGame();
        }

        /*if this point is reached, then only the player who lasted moved is not blocked.
         *So keep the current player as it is so they can still play until they are blocked.
         *This is needed for the bots to continue playing to the end*/
        informPlayersOfTurn();
    }

    /*Methods to allow testing of the Game object
     *Allows the game board to be set to a specific state*/
    public void setGameBoard(int[][] board){
        gameBoard = board;
    }

    //Places a tile with a mark without checking if its valid
    public void setTile(int x, int y, int playerMark){
        gameBoard[x][y] = playerMark;
    }

    //Needed to ensure normal game rules apply (Don't get the first turn exemption)
    public void setHadFirstTrue(){
        hadFirst = new boolean[]{true, true, true, true, true, true};
    }

    //Allows public access to private method checkAdjacent()
    public boolean useCheckAdjacent(int x, int y, int playerMark){
        return checkAdjacent(x, y, playerMark);
    }
}

