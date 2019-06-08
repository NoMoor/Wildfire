package wildfire.wildfire.utils;

import rlbot.flat.BallPrediction;
import wildfire.input.BallData;
import wildfire.input.CarData;
import wildfire.input.DataPacket;
import wildfire.vector.Vector2;
import wildfire.vector.Vector3;
import wildfire.wildfire.obj.PredictionSlice;

public class Behaviour {

	/**
	 * Acceleration = 2(Displacement - Initial Velocity * Time) / Time^2
	 */
	public static PredictionSlice getEarliestImpactPoint(DataPacket input, BallPrediction ballPrediction){
		//"NullPointerException - Lookin' good!"
		if(ballPrediction == null){
			System.err.println("NullPointerException - Lookin' good!");
			return new PredictionSlice(input.ball.position, 0);
		}
		
		Vector2 carPosition = input.car.position.flatten(); 
		double initialVelocity = input.car.velocity.flatten().magnitude();
		
		for(int i = 0; i < ballPrediction.slicesLength(); i++){
			Vector3 location = Vector3.fromFlatbuffer(ballPrediction.slices(i).physics().location());
			
			double displacement = location.flatten().distance(carPosition) - Constants.BALLRADIUS;
			double timeLeft = (double)i / 60D;
			double acceleration = 2 * (displacement - initialVelocity * timeLeft) / Math.pow(timeLeft, 2);
			
			if(initialVelocity + acceleration * timeLeft < Math.max(initialVelocity, Physics.boostMaxSpeed(initialVelocity, input.car.boost))){
				return new PredictionSlice(location.plus(carPosition.minus(location.flatten()).normalized().scaled(Constants.BALLRADIUS).withZ(0)), i);
			}
		}
		
		//Return final position as fallback
		return new PredictionSlice(Vector3.fromFlatbuffer(ballPrediction.slices(ballPrediction.slicesLength() - 1).physics().location()), ballPrediction.slicesLength() - 1);
	}

	public static boolean isTeammateCloser(DataPacket input, Vector3 target){
		double ourDistance = target.distance(input.car.position);
		for(byte i = 0; i < input.cars.length; i++){
			CarData c = input.cars[i];
			if(i == input.playerIndex || c == null || c.isDemolished || c.team != input.car.team) continue;
			if(ourDistance > target.distance(c.position)) return true;
		}
		return false;
	}

	public static boolean isTeammateCloser(DataPacket input){
		return isTeammateCloser(input, input.ball.position);
	}

	public static boolean isTeammateCloser(DataPacket input, Vector2 target){
		return isTeammateCloser(input, target.withZ(0));
	}

	public static boolean hasTeammate(DataPacket input){
		for(byte i = 0; i < input.cars.length; i++){
			if(i != input.playerIndex && input.cars[i].team == input.car.team) return true;
		}
		return false;
	}

	public static CarData closestOpponent(DataPacket input, Vector3 target){
		CarData best = null;
		double bestDistance = Double.MAX_VALUE;
		for(CarData c : input.cars){
			if(c == null || c.isDemolished || c.team == input.car.team) continue;
			double distance = c.position.distance(target);
			if(distance < bestDistance){
				best = c;
				bestDistance = distance;
			}
		}
		return best;
	}

	public static double closestOpponentDistance(DataPacket input, Vector3 target){
		CarData opponent = closestOpponent(input, target);
		return opponent == null ? Double.MAX_VALUE : opponent.position.distance(input.ball.position);
	}

	public static boolean hasOpponent(DataPacket input){
		for(byte i = 0; i < input.cars.length; i++){
			if(input.cars[i].team != input.car.team) return true;
		}
		return false;
	}

	/**
	 * Returns a 2D vector of a point inside of the enemy's goal,
	 * it should be a good place to shoot relative to this car
	 */
	public static Vector2 getTarget(CarData car, BallData ball){
		final double goalSafeZone = 750D;
		Vector2 target = null;
		Vector2 ballDifference = ball.position.minus(car.position).flatten();
		ballDifference = ballDifference.scaled(1D / Math.abs(ballDifference.y)); //Make the Y-value 1
		if(car.team == 0 && ballDifference.y > 0){
			double distanceFromGoal = Constants.PITCHLENGTH - ball.position.y;
			ballDifference = ballDifference.scaled(distanceFromGoal);
			target = ball.position.flatten().plus(ballDifference);
		}else if(car.team == 1 && ballDifference.y < 0){
			double distanceFromGoal = Constants.PITCHLENGTH + ball.position.y;
			ballDifference = ballDifference.scaled(distanceFromGoal);
			target = ball.position.flatten().plus(ballDifference);
		}
		if(target != null){
			target = new Vector2(Math.max(-goalSafeZone, Math.min(goalSafeZone, target.x)), target.y);
			return target;
		}
		return Constants.enemyGoal(car.team);
	}

	/*
	 * Returns whether the trace goes into the opponent's goal
	 */
	public static boolean isInCone(CarData car, Vector3 target, double threshold){
		if(Utils.teamSign(car) * car.position.y > Constants.PITCHLENGTH) return false; //Inside enemy goal
		Vector2 trace = Utils.traceToY(car.position.flatten(), target.minus(car.position).flatten(), Utils.teamSign(car) * Constants.PITCHLENGTH);
		return trace != null && Math.abs(trace.x) < Constants.GOALHALFWIDTH + threshold;
	}

	public static boolean isInCone(CarData car, Vector3 target){
		return isInCone(car, target, -Constants.BALLRADIUS);
	}

	/*
	 * Returns whether the trace goes into our own goal (inverse cone)
	 */
	public static boolean isTowardsOwnGoal(CarData car, Vector3 target, double threshold){
		if(Utils.teamSign(car) * car.position.y < -Constants.PITCHLENGTH) return false; //Inside own goal
		Vector2 trace = Utils.traceToY(car.position.flatten(), target.minus(car.position).flatten(), Utils.teamSign(car) * -Constants.PITCHLENGTH);
		return trace != null && Math.abs(trace.x) < Constants.GOALHALFWIDTH + threshold;
	}

	public static boolean isTowardsOwnGoal(CarData car, Vector3 target){
		return isTowardsOwnGoal(car, target, Constants.BALLRADIUS);
	}

	public static boolean isOpponentBehindBall(DataPacket input){
		for(byte i = 0; i < input.cars.length; i++){
			CarData car = input.cars[i];
			if(car == null || car.team == input.car.team) continue;
			if(correctSideOfTarget(car, input.ball.position)) return true;
		}
		return false;
	}

	public static boolean correctSideOfTarget(CarData car, Vector2 target){
		return Math.signum(target.y - car.position.y) == Utils.teamSign(car);
	}

	public static boolean correctSideOfTarget(CarData car, Vector3 target){
		return correctSideOfTarget(car, target.flatten());
	}

	public static boolean defendNotReturn(DataPacket input, Vector3 impactPoint, double homeZoneSize, boolean onTarget){
		return onTarget || Utils.teamSign(input.car.team) * input.ball.velocity.y < -1100 || impactPoint.distanceFlat(Constants.homeGoal(input.car.team)) < homeZoneSize;
	}

	public static boolean isKickoff(DataPacket input){
		return input.ball.velocity.isZero() && input.ball.position.flatten().isZero();
	}

	public static boolean isCarAirborne(CarData car){
		return !car.hasWheelContact && (car.position.z > 150 || Math.abs(car.velocity.z) > 280);
	}

	public static boolean isBallAirborne(BallData ball){
		return ball.position.z > 290 || Math.abs(ball.velocity.z) > 250;
	}

	public static Vector3 getBounce(BallPrediction ballPrediction){
		if(ballPrediction == null){
			System.err.println("NullPointerException - Lookin' good!");
			return null;
		}
		
	    for(int i = 0; i < ballPrediction.slicesLength(); i++){
	    	Vector3 location = Vector3.fromFlatbuffer(ballPrediction.slices(i).physics().location());
	    	if(location.z <= Constants.BALLRADIUS + 15) return location;
	    }
	    return null;
	}

	/**
	 * Time from now for the time to touch the floor, 
	 * measured in seconds
	 */
	public static double getBounceTime(BallPrediction ballPrediction){
		if(ballPrediction == null){
			System.err.println("NullPointerException - Lookin' good!");
			return 360;
		}
		
	    for(int i = 0; i < ballPrediction.slicesLength(); i++){
	    	if(Vector3.fromFlatbuffer(ballPrediction.slices(i).physics().location()).z <= Constants.BALLRADIUS + 15){
	    		return (double)i / 60D;
	    	}
	    }
	    return -1;
	}

	public static boolean isOnWall(CarData car){
		return car.position.z > 240 && car.hasWheelContact;
	}

	/*
	 * Whether the ball will go in this team's goal
	 */
	public static boolean isOnTarget(BallPrediction ballPrediction, int team){
		if(ballPrediction == null) return false;
	    for(int i = 0; i < ballPrediction.slicesLength(); i++){
	    	Vector3 location = Vector3.fromFlatbuffer(ballPrediction.slices(i).physics().location());
	    	if(Math.abs(location.y) >= Constants.PITCHLENGTH) return Math.signum(location.y) != Utils.teamSign(team);
	    }
	    return false;
	}

	public static boolean isOnPrediction(BallPrediction ballPrediction, Vector3 vec){
		for(int i = 0; i < ballPrediction.slicesLength(); i++){
			Vector3 location = Vector3.fromFlatbuffer(ballPrediction.slices(i).physics().location());
			Vector3 velocity = Vector3.fromFlatbuffer(ballPrediction.slices(i).physics().velocity());
			
			double distance = vec.distance(location);
			if(distance < velocity.scaled(1D / 60).magnitude()) return true;
		}
		return false;
	}
	
	public static CarData getGoalkeeper(CarData[] cars, int team){
		final double maxGoalDistance = 2900;
		
		Vector2 goal = Constants.homeGoal(team);
		
		for(CarData car : cars){
			if(car.team != team) continue;
			if(car.position.flatten().distance(goal) < maxGoalDistance) return car;
		}
		
		return null;
	}

}