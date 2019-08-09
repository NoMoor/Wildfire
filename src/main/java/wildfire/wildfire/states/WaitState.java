package wildfire.wildfire.states;

import java.awt.Color;
import java.awt.Point;

import wildfire.input.CarData;
import wildfire.input.DataPacket;
import wildfire.output.ControlsOutput;
import wildfire.vector.Vector2;
import wildfire.vector.Vector3;
import wildfire.wildfire.Wildfire;
import wildfire.wildfire.actions.AerialAction;
import wildfire.wildfire.actions.DodgeAction;
import wildfire.wildfire.actions.RecoveryAction;
import wildfire.wildfire.actions.SmartDodgeAction;
import wildfire.wildfire.handling.Handling;
import wildfire.wildfire.obj.PredictionSlice;
import wildfire.wildfire.obj.State;
import wildfire.wildfire.utils.Behaviour;
import wildfire.wildfire.utils.Constants;
import wildfire.wildfire.utils.Physics;
import wildfire.wildfire.utils.Utils;

public class WaitState extends State {

	private final boolean renderJump = true;
	private boolean alwaysSmartDodge;
	
	/*
	 * How far we want to be from the ball's bounce
	*/
	private final double desiredDistanceGround = 44;
	private final double offsetDecrement = 0.05;

	private Vector2 bounce = null;
	private double timeLeft = 0, pathDistanceChosen;
	private double bounceDistance;

	public WaitState(Wildfire wildfire, boolean alwaysSmartDodge){
		super("Wait", wildfire);
		this.alwaysSmartDodge = alwaysSmartDodge;
	}

	@Override
	public boolean ready(DataPacket input){
		Vector3 bounce3 = Behaviour.getBounce(wildfire.ballPrediction);
		if(bounce3 == null) return false;
		
		bounce = bounce3.flatten();
		bounce = bounce.plus(bounce.minus(Behaviour.getTarget(input.car, bounce)).scaledToMagnitude(desiredDistanceGround));
		timeLeft = Behaviour.getBounceTime(wildfire.ballPrediction);
		bounceDistance = bounce.distance(input.car.position.flatten());
		
		//Wall hit
		double wallDistance = Utils.distanceToWall(wildfire.impactPoint.getPosition());
		if(wildfire.impactPoint.getPosition().y * Utils.teamSign(input.car) < 1500 
				&& wallDistance < 260 && Math.abs(wildfire.impactPoint.getPosition().x) > 1500){
			return false;
		}
		
		//Can't reach point (optimistic)
		if(bounceDistance / timeLeft > 2700) return false;
		
		//Teammate's closer
		if(Behaviour.isTeammateCloser(input, bounce)) return false;
		
		//Opponent's corner
		if(Utils.teamSign(input.car) * bounce.y > 4000 && Constants.enemyGoal(input.car.team).distance(bounce) > 1800 && !Behaviour.isInCone(input.car, bounce3, 1000)) return bounceDistance < 2400;
		
		return (Behaviour.isBallAirborne(input.ball) && input.ball.velocity.flatten().magnitude() < 5000) || (input.ball.position.z > 110 && input.ball.position.distanceFlat(input.car.position) < 220 && wallDistance > 450);
	}

	@Override
	public ControlsOutput getOutput(DataPacket input){		
		// We're evolved now, no longer shooting at the centre!
//		Vector2 enemyGoal = Constants.enemyGoal(input.car.team);
		Vector2 enemyGoal = Behaviour.getTarget(input.car, bounce);
		
		//Aerial
		boolean onTarget = Behaviour.isOnTarget(wildfire.ballPrediction, input.car.team);
		double impactRadians = Handling.aim(input.car, wildfire.impactPoint.getPosition().flatten());
		if(wildfire.impactPoint.getPosition().z > (onTarget && Math.abs(input.car.position.y) > 4500 ? 220 : 700) && Behaviour.correctSideOfTarget(input.car, wildfire.impactPoint.getPosition()) && input.car.hasWheelContact
				&& Math.abs(impactRadians) < (onTarget ? 0.42 : 0.32) && wildfire.impactPoint.getPosition().y * Utils.teamSign(input.car) < (onTarget ? -1500 : -2500) && (onTarget || Utils.teamSign(input.car) * input.ball.velocity.y < -1000)){
//		if(timeLeft > 1.5 && (input.car.position.z > 120 || Math.abs(impactRadians) < 0.35)){
			currentAction = AerialAction.fromBallPrediction(this, input.car, wildfire.ballPrediction, wildfire.impactPoint.getPosition().z > 420);
			if(currentAction != null && !currentAction.failed) return currentAction.getOutput(input);
			currentAction = null;
		}

		wildfire.renderer.drawCircle(Color.YELLOW, bounce, Constants.BALLRADIUS);
		boolean towardsOwnGoal = Behaviour.isTowardsOwnGoal(input.car, bounce.withZ(0), 240);
		
		//Smart dodge
		boolean planSmartDodge = false, smartDodgeCone = false;
		if(alwaysSmartDodge){
			planSmartDodge = true;
		}else if(!towardsOwnGoal && input.car.hasWheelContact && input.car.position.z < 200 && input.ball.position.z > 250){
			if(Behaviour.closestOpponentDistance(input, bounce.withZ(Constants.BALLRADIUS)) < 1800){
				planSmartDodge = true;
			}else if(bounce.distance(enemyGoal) < 2500){
				planSmartDodge = true;
				smartDodgeCone = true;
			}else if(bounce.distance(Constants.homeGoal(input.car.team)) < 2500){
				planSmartDodge = true;
			} 
		}
		PredictionSlice smartDodgeCandidate = (planSmartDodge ? SmartDodgeAction.getCandidateLocation(wildfire.ballPrediction, input.car, enemyGoal) : null);
		planSmartDodge = (planSmartDodge && smartDodgeCandidate != null); // && smartDodgeCandidate.getPosition().z > 150
//		wildfire.renderer.drawString2d("Plan Smart Dodge: " + planSmartDodge, Color.WHITE, new Point(0, 40), 2, 2);
		double desiredDist = getDesiredDistance(input.car, planSmartDodge ? smartDodgeCandidate.getPosition() : null);
		wildfire.renderer.drawCircle(Color.ORANGE, bounce, desiredDist);
		if(planSmartDodge){
			currentAction = new SmartDodgeAction(this, input, smartDodgeCone);
			if(!currentAction.failed && ((SmartDodgeAction)currentAction).target.getPosition().z > 220){ //
				return currentAction.getOutput(input);
			}
			currentAction = null;
		}
		
		// Drive down the wall.
		if(Behaviour.isOnWall(input.car) && bounce.withZ(Constants.BALLRADIUS).distance(input.car.position) > 700){
			wildfire.renderer.drawString2d("Wall", Color.WHITE, new Point(0, 20), 2, 2);
			return Handling.driveDownWall(input);
		}else if(Behaviour.isCarAirborne(input.car)){
			currentAction = new RecoveryAction(this, input.elapsedSeconds);
			return currentAction.getOutput(input);
		}
		
		wildfire.renderer.drawString2d("Time: " + Utils.round(timeLeft) + "s", Color.WHITE, new Point(0, 20), 2, 2);
		
		// Make use of the candidate position from the smart dodge.
		if(planSmartDodge) return Handling.arriveAtSmartDodgeCandidate(input.car, smartDodgeCandidate, wildfire.renderer);
		
		// Catch (don't move out the way anymore).
		if(input.car.position.distanceFlat(bounce) < desiredDistanceGround / 4 && Behaviour.correctSideOfTarget(input.car, bounce)){
			wildfire.renderer.drawString2d("Catch", Color.WHITE, new Point(0, 60), 2, 2);
//			return new ControlsOutput().withBoost(false).withSteer((float)-Handling.aim(input.car, bounce) * 2F).withThrottle((float)-input.car.forwardVelocity);
			return Handling.stayStill(input.car);
		}
		
		double timeEffective = timeLeft;
		Vector2 destination = bounce;
				
		Vector2 target = null;
		double velocityNeeded = -1;
		
		for(double offset = (towardsOwnGoal ? 0.9 : (bounceDistance > 2000 ? 1.2 : 0.6)); offset > (towardsOwnGoal ? 0.2 : 0); offset -= offsetDecrement){
			target = getNextPoint(input.car.position.flatten(), destination, enemyGoal, offset);
			
			double distance = getPathwayDistance(input.car.position.flatten(), destination, enemyGoal, offset, Color.YELLOW, desiredDist);
			double initialVelocity = input.car.velocityDir(target.minus(input.car.position.flatten()));
			double finalVelocity = (2 * distance) / timeEffective - initialVelocity;
			
			if(finalVelocity <= Math.max(initialVelocity, Physics.boostMaxSpeed(initialVelocity, input.car.boost))){
				velocityNeeded = distance / timeEffective;
				
				wildfire.renderer.drawCircle(Color.GREEN, target, Math.min(4, Math.max(0.5, timeLeft)) * 30D);
				pathDistanceChosen = getPathwayDistance(input.car.position.flatten(), destination, enemyGoal, offset, Color.GREEN, desiredDist); //Redraw in pretty green!
				
				wildfire.renderer.drawString2d("Offset: " + (int)(offset * 100) + "%", Color.WHITE, new Point(0, 60), 2, 2);
				wildfire.renderer.drawString2d("Distance: " + (int)pathDistanceChosen + "uu", Color.WHITE, new Point(0, 80), 2, 2);
				
				break;
			}
		}
		
		boolean noPoint = (velocityNeeded == -1);
		if(noPoint) target = destination;
		
		target = Behaviour.goalStuck(input.car, target);
		double steerRadians = Handling.aim(input.car, target);
		
		//No point found
		if(noPoint){
			pathDistanceChosen = target.distance(input.car.position.flatten());
			velocityNeeded = pathDistanceChosen / timeEffective;			
//			wildfire.renderer.drawString2d("Velocity Needed: " + (int)velocityNeeded + "uu/s", Color.WHITE, new Point(0, 60), 2, 2);
			
			boolean dribble = (input.ball.position.z > 100 && input.ball.position.distance(input.car.position) < 290);
			
			// Dodge.
			if(!dribble && !towardsOwnGoal && input.car.velocity.magnitude() > 950){
				if(Math.abs(steerRadians) < Math.PI * 0.2 && (input.car.position.distanceFlat(destination) < 620 && input.ball.position.z < 300)){
					currentAction = new DodgeAction(this, steerRadians, input);
					if(!currentAction.failed) return currentAction.getOutput(input);
				}
				currentAction = null;
			}
		}
		
		// Handling.
	    double steer = steerRadians * -3;
		ControlsOutput controls = new ControlsOutput().withSteer(steer);
		double currentVelocity = input.car.velocityDir(target.minus(input.car.position.flatten()));
		double acceleration = ((velocityNeeded - currentVelocity) / timeEffective);
		if(Math.abs(steerRadians) > 0.4){
			// Turn.
			controls.withThrottle(Math.signum(acceleration)).withSlide(Handling.canHandbrake(input.car) && Math.abs(steerRadians) > 1);
		}else if((acceleration > Constants.BOOSTACC || velocityNeeded > 1410) && pathDistanceChosen > 500){
			controls.withThrottle(1).withBoost(Math.abs(steerRadians) < 0.25);
		}else{
			controls.withThrottle(acceleration / 1000);
		}
		
	    return controls;
	}
	
	public double getPathwayDistance(Vector2 start, Vector2 bounce, Vector2 enemyGoal, double length, Color colour, double desiredDist){
		double distance = 0;
		for(int i = 0; i < 16; i++){
			double dist = start.distance(bounce);
			if(i != 15){
				Vector2 next = getNextPoint(start, bounce, enemyGoal, length);
				
				double distanceStart = next.distance(start);
				double distanceBounce = next.distance(bounce);
				
				if(distanceBounce > desiredDist){
					wildfire.renderer.drawLine3d(colour, start.toFramework(), next.toFramework());
					distance += start.distance(next);
					start = next;	
				}else{
					wildfire.renderer.drawLine3d(colour, start.toFramework(), start.plus(next.minus(start).scaledToMagnitude(dist - desiredDist)).toFramework());
					distance += distanceStart + (distanceBounce - desiredDist);
					break;
				}
			}else{
				//Final point
				wildfire.renderer.drawLine3d(colour, start.toFramework(), start.plus(bounce.minus(start).scaledToMagnitude(dist - desiredDist)).toFramework());
				distance += (dist - desiredDist); //We only need to reach the side of the ball
				break;
			}
		}
		return distance;
	}
	
	private Vector2 getNextPoint(Vector2 start, Vector2 bounce, Vector2 enemyGoal, double length){
		double offset = length * bounce.distance(start) / 2.3;
		Vector2 target = bounce.plus(bounce.minus(enemyGoal).scaledToMagnitude(offset));
		target = start.plus(target.minus(start).scaled(0.225)).confine();
		
		return target;
	}
	
	private Vector3 jump(CarData car){
		Vector3 velocity = car.velocity.withZ(SmartDodgeAction.jumpVelocity); //Max jump velocity
		if(velocity.magnitude() > 2300) velocity.scaledToMagnitude(2300);
		return simJump(car, car.position, velocity);
	}
	
	private Vector3 simJump(CarData car, Vector3 start, Vector3 velocity){
		if(start.isOutOfBounds()) return start;
		
		final double scale = (1D / 60);
		velocity = velocity.plus(new Vector3(0, 0, -Constants.GRAVITY * scale)); //Gravity
		if(velocity.magnitude() > 2300) velocity.scaledToMagnitude(2300);
		boolean up = (velocity.z > 0);
		
		Vector3 next = start.plus(velocity.scaled(scale));
		if(renderJump) wildfire.renderer.drawLine3d((velocity.z > 0 ? Color.CYAN : Color.GRAY), start.toFramework(), next.toFramework());
		
		Vector3 continued = simJump(car, next, velocity);
		return (up ? continued : start);
	}

	
	public double getDesiredDistance(CarData car, Vector3 smartDodgeCandidate){
		if(smartDodgeCandidate == null) return desiredDistanceGround;
		
		Vector3 jump = jump(car);
//		if(Utils.toLocal(car, jump).y < 0) return 0;
		return jump.distanceFlat(car.position);
	}
	
}
