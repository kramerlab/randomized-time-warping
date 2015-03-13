package utils.dtw;

import java.util.Arrays;
//import java.util.Locale;
//import java.text.DecimalFormat;
//import java.text.DecimalFormatSymbols;
import java.util.Random;

import utils.timeseries.TimeSeries;
import utils.distance.DistanceFunction;
import utils.dtw.WarpInfo;

public class DynamicTimeWarping {
	private double[][] costMatrix;
	private Random rand;
	
	public DynamicTimeWarping() {
		
	}
	
	public DynamicTimeWarping(int szI, int szJ) {
		costMatrix = new double[szI][szJ];
		rand = new Random();
	}
	
	public WarpInfo getHeuristicDTW(TimeSeries tsI, TimeSeries tsJ, DistanceFunction distFn, int windowPercent, int distribution) {
		int maxI = tsI.size();			// Maximum index number for TimeSeries I
		int maxJ = tsJ.size();			// maximum index number for TimeSeries J
		int i = 0;						// Current index number for TimeSeries I
		int j = 0;						// Current index number for TimeSeries J
		double MEAN = 1.0, STD_DEV = 1.0/3.0;//, VARIANCE = STD_DEV*STD_DEV;

		WarpInfo info = new WarpInfo();	// Warping Path info (e.g. length and path indices)
		
		double costDiag, costRight, costDown;	// cost variables for prospective successive directions 
		double costSum;							// cumulative cost 
		
		double[] probs = new double[2];			// Used to save the probabilities of the direction to take
		double selProb;							// Selection probability
		boolean isValidCellChosen;

		int w;	// window size for calculation of cost matrix entries if windowSize is zero we got to a 1 length window equal to euclidean dist
		if (windowPercent == 0) {
			w = 1;
		} else {
			w = Math.max( (int) Math.ceil( windowPercent*maxI/100.0 ), Math.abs(maxI-maxJ));
		}
		
		for(double[] current : costMatrix) {	// Assign positive infinity to entire matrix
			Arrays.fill(current, Double.POSITIVE_INFINITY);
		}

		costMatrix[0][0] = distFn.calcDistance(tsI.get(i), tsJ.get(j));
		info.addLast(i, j);
		while(i<maxI && j<maxJ) {
			if(i+1<maxI && j+1<maxJ) {		// Check if move to diagonal element is valid
				costDiag = distFn.calcDistance(tsI.get(i+1), tsJ.get(j+1));
			} else {
				costDiag = 1e12;
			}
			//if(i+1<maxI && Math.abs(i+1-j)<w) { // OLD Conditional, following is better to understand
			if(i+1<Math.min(w+j, maxI)) {	// Check if moving downwards is valid
				costDown = distFn.calcDistance(tsI.get(i+1), tsJ.get(j));
			} else {
				costDown = 1e12;
			}
			//if(j+1<maxJ && Math.abs(j+1-i)<maxI) { // OLD Conditional, following is better to understand
			if(j+1<Math.min(w+i, maxJ)) {	// Check if moving right is valid
				costRight = distFn.calcDistance(tsI.get(i), tsJ.get(j+1));
			} else {
				costRight = 1e12;
			}
			isValidCellChosen = false;
//			double epsilon = 1e-9, epsilon3x = 3e-9;
//			costSum = costDiag+costRight+costDown;
//			probs[0] = (costSum-costRight+epsilon) / (costSum + epsilon3x);
//			probs[1] = (costSum-costDiag+epsilon) / (costSum + epsilon3x) + probs[0];
			double alpha = 5;
			costRight = Math.exp(-alpha * costRight);
			costDiag = Math.exp(-alpha * costDiag);
			costDown = Math.exp(-alpha * costDown);
			costSum = (costRight + costDiag + costDown)/2.0;  
			probs[0] = costRight/costSum;
			probs[1] = costDiag/costSum+probs[0];
			while(!isValidCellChosen) {		// loop used for times when we are at the end of warping path but a valid cell can't be chosen
				if (distribution == 1) {
					selProb = rand.nextDouble() * 2.0;	// generate a uniform random number
					// the random number is between 0 and 1 so we multiply it with
					// 2 to get it between 0 and 2 
				} else {
					selProb = MEAN + rand.nextGaussian()*STD_DEV;	// generate a normally distributed
					// random number, it has a mean at 0 and a std of 1, so we add MEAN to it
					// to shift it's mean to 1 and multiply it with STANDARD DEVIATION to generate random
					// numbers within required Standard Deviation
					// Previously we were checking the selProb each time we got a random number and
					// restricted it between 0 and 2 however it was counterproductive. because we were only
					// basing our decision on whether the number was < or > a given probability so even if
					// it turns out to be < 0 or > 2, it still is a good number to base our decision
				}
				// Previously, we are checking if the selProb was <= probs[0] then we chose to go right.
				// If selProb was > probs[0] but <= probs[1] we moved to the diagonal.
				// and if it was > probs[1] as well we moved to the down ward location
				// however that was inefficient. now we are generating 2 probabilities instead of 3 and
				// only check for the selProb be to < probs[0] to go right or > probs[1] to go down and
				// to go diagonally if none of the 2 cases are true
				if(selProb < probs[0] && i<maxI && j+1<maxJ && j+1<j+w) {	// j+1<j+w added to restrict going out of window
					// Moving one cell Right
					costMatrix[i][j+1] = costMatrix[i][j] + costRight;
					j++;
					isValidCellChosen = true;
				} else if(selProb > probs[1] && i+1<maxI && j<maxJ && i+1<i+w) {	// i+1<i+w added to restrict going out of window
					// Moving one cell Down
					costMatrix[i+1][j] = costMatrix[i][j] + costDown;
					i++;
					isValidCellChosen = true;
				} else if(i+1<maxI && j+1<maxJ) {			// OLD Condition selProb <= probs[1] && i+1<maxI && j+1<maxJ 
					// Moving diagonally
					costMatrix[i+1][j+1] = costMatrix[i][j] + costDiag;
					i++; j++;
					isValidCellChosen = true;
				}
				if(isValidCellChosen) {
					info.addLast(i, j);
					Arrays.fill(probs, 0);				// reinitialize the probs array to all zeros
					break;
				}
			}
			if(i+1==maxI && j+1==maxJ) {
				info.setWarpDistance(costMatrix[i][j]);
				break;
			}
		}
		return info;
	}
	
	public WarpInfo getLuckyDTW(TimeSeries tsI, TimeSeries tsJ, DistanceFunction distFn, int windowPercent) {
		int maxI = tsI.size();			// Maximum index number for TimeSeries I
		int maxJ = tsJ.size();			// maximum index number for TimeSeries J
		int i = 0;						// Current index number for TimeSeries I
		int j = 0;						// Current index number for TimeSeries J
		WarpInfo info = new WarpInfo();	// Warping Path info (e.g. length and path indices)
		
		double costDiag, costRight, costDown;	// cost variables for prospective successive directions 
		
		int w;	// window size for calculation of cost matrix entries if windowSize is zero we got to a 1 length window equal to euclidean dist
		if (windowPercent == 0) {
			w = 1;
		} else {
			w = Math.max( (int) Math.ceil( windowPercent*maxI/100.0 ), Math.abs(maxI-maxJ));
		}
		
		for(double[] current : costMatrix) {	// Assign positive infinity to entire matrix
			Arrays.fill(current, Double.POSITIVE_INFINITY);
		}
		costMatrix[0][0] = distFn.calcDistance(tsI.get(i), tsJ.get(j));
		info.addLast(i, j);
		while(i<maxI && j<maxJ) {
			if(i+1<maxI && j+1<maxJ) {
				costDiag = distFn.calcDistance(tsI.get(i+1), tsJ.get(j+1));
			} else {
				costDiag = 1e12;
			}
			//if(i+1<maxI && Math.abs(i+1-j)<w) { // OLD Conditional, following is better to understand
			if(i+1<Math.min(w+j, maxI)) {
				costDown = distFn.calcDistance(tsI.get(i+1), tsJ.get(j));
			} else {
				costDown = 1e12;
			}
			//if(j+1<maxJ && Math.abs(j+1-i)<maxI) { // OLD Conditional, following is better to understand
			if(j+1<Math.min(w+i, maxJ)) {
				costRight = distFn.calcDistance(tsI.get(i), tsJ.get(j+1));
			} else {
				costRight = 1e12;
			}
			if ((costDiag <= costRight) && (costDiag <= costDown)) {
				costMatrix[i+1][j+1] = costMatrix[i][j] + costDiag;
				i++;
				j++;
			} else if ((costRight < costDiag) && (costRight < costDown)) {
				costMatrix[i][j+1] = costMatrix[i][j] + costRight;
				j++;
			} else if ((costDown < costDiag) && (costDown < costRight)) {
				costMatrix[i+1][j] = costMatrix[i][j] + costDown;
				i++;
			} else { // costDown==costRight > costDiag
				if(Math.random()<0.5) {	// Go down
					costMatrix[i+1][j] = costMatrix[i][j] + costDown;
					i++;
				} else {				// Go right
					costMatrix[i][j+1] = costMatrix[i][j] + costRight;
					j++;
				}
			}
			info.addLast(i, j);
			if(i+1==maxI && j+1==maxJ) {
				info.setWarpDistance(costMatrix[i][j]);
				break;
			}
		}
		return info;
	}
	
	public WarpInfo getNormalDTW(TimeSeries tsI, TimeSeries tsJ, DistanceFunction distFn, int windowPercent) {
		int maxI = tsI.size();
		int maxJ = tsJ.size();
		for(double[] current : costMatrix) {
			Arrays.fill(current, Double.POSITIVE_INFINITY);
		}
		if(windowPercent<100) {
			int w;	// window size for calculation of cost matrix entries if windowSize is zero we got to a 1 length window equal to euclidean dist
			if (windowPercent == 0) {
				w = 1;
			} else {
				w = Math.max( (int) Math.ceil( windowPercent*maxI/100.0 ), Math.abs(maxI-maxJ));
			}

			costMatrix[0][0] = distFn.calcDistance(tsI.get(0), tsJ.get(0));
			for(int j=1; j<w; j++) {
				costMatrix[0][j] = costMatrix[0][j-1] + distFn.calcDistance(tsI.get(0), tsJ.get(j));
			}
			// First loop set
			for(int i=1; i<w; i++) {
				costMatrix[i][0] = costMatrix[i - 1][0] + distFn.calcDistance(tsI.get(i), tsJ.get(0));
				for(int j=1; j<i+w; j++) {
					costMatrix[i][j] = calcCost(tsI, tsJ, distFn, costMatrix, i, j);
				}
			}
			// Second loop set
			int k=1;
			for(int i=w; i<maxI-w; i++, k++) {
				for(int j=k; j<i+w; j++) {
					costMatrix[i][j] = calcCost(tsI, tsJ, distFn, costMatrix, i, j);
				}
			}
			// Third loop set
			for(int i=maxI-w; i<maxI; i++, k++) {
				for(int j=k; j<maxJ; j++) {
					costMatrix[i][j] = calcCost(tsI, tsJ, distFn, costMatrix, i, j);
				}
			}
		} else {
			costMatrix[0][0] = distFn.calcDistance(tsI.get(0), tsJ.get(0));
			for(int j=1; j<maxJ; j++) {
				costMatrix[0][j] = costMatrix[0][j-1] + distFn.calcDistance(tsI.get(0), tsJ.get(j));
			}
			for(int i=1; i<maxI; i++) {
				costMatrix[i][0] = costMatrix[i - 1][0] + distFn.calcDistance(tsI.get(i), tsJ.get(0));				
				for(int j=1; j<maxJ; j++) {
					costMatrix[i][j] = calcCost(tsI, tsJ, distFn, costMatrix, i, j);
				}
			}
		}
		double costDiag, costLeft, costDown;
		int i = tsI.size()-1;	// tsI_size and tsJ_size have lengths of time series so subtract 
		int j = tsJ.size()-1;	// 1 from them to point to the last element of the cost matrix
		double minDist = costMatrix[i][j];
		WarpInfo info = new WarpInfo();
		info.setWarpDistance(minDist);
		info.addFirst(i, j);
		while( (i>0) || (j>0) ) {
			if ((i > 0) && (j > 0))
				costDiag = costMatrix[i - 1][j - 1];
			else
				costDiag = Double.POSITIVE_INFINITY;

			if (j > 0)
				costLeft = costMatrix[i][j - 1];
			else
				costLeft = Double.POSITIVE_INFINITY;

			if (i > 0)
				costDown = costMatrix[i - 1][j];
			else
				costDown = Double.POSITIVE_INFINITY;

			// Prefer moving diagonally and moving towards the i==j axis  
			// of the matrix if there are ties.
			if ((costDiag <= costLeft) && (costDiag <= costDown)) {
				i--;
				j--;
			} else if ((costLeft < costDiag) && (costLeft < costDown)) {
				j--;
			} else if ((costDown < costDiag) && (costDown < costLeft)) {
				i--;
			} else if (i <= j) { // leftCost==rightCost > diagCost
				i--;
			} else { // leftCost==rightCost > diagCost
				j--;
			}
			info.addFirst(i, j);
		}
		return info;
	}

	private double calcCost(TimeSeries tsI, TimeSeries tsJ, DistanceFunction distFn, double[][] costMatrix, int i, int j) {
		return distFn.calcDistance(tsI.get(i), tsJ.get(j))+ Math.min(costMatrix[i - 1][j], Math.min(costMatrix[i - 1][j - 1], costMatrix[i][j - 1]));
	}
}