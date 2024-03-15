package frc.team3128.commands;

import common.hardware.input.NAR_XboxController;
import common.utility.Log;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.ScheduleCommand;
import edu.wpi.first.wpilibj2.command.StartEndCommand;
import frc.team3128.Robot;
import frc.team3128.RobotContainer;
import frc.team3128.Constants.IntakeConstants;
import frc.team3128.Constants.ShooterConstants;
import frc.team3128.Constants.LedConstants.Colors;
import frc.team3128.subsystems.AmpMechanism;
import frc.team3128.subsystems.Climber;
import frc.team3128.subsystems.Intake;
import frc.team3128.subsystems.Leds;
import frc.team3128.subsystems.Shooter;
import frc.team3128.subsystems.Swerve;
import frc.team3128.subsystems.Climber.Setpoint;

import java.util.function.DoubleSupplier;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.path.PathConstraints;

import static edu.wpi.first.wpilibj2.command.Commands.*;
import static frc.team3128.Constants.ShooterConstants.MAX_RPM;
import static frc.team3128.Constants.ShooterConstants.RAM_SHOT_RPM;
import static frc.team3128.Constants.SwerveConstants.RAMP_TIME;
import static frc.team3128.Constants.SwerveConstants.maxAcceleration;
import static frc.team3128.Constants.SwerveConstants.maxAngularAcceleration;
import static frc.team3128.Constants.SwerveConstants.maxAngularVelocity;
import static frc.team3128.Constants.SwerveConstants.maxAttainableSpeed;

public class CmdManager {

    private static Swerve swerve = Swerve.getInstance();
    private static Intake intake = Intake.getInstance();
    private static Shooter shooter = Shooter.getInstance();
    private static Climber climber = Climber.getInstance();
    private static AmpMechanism ampMechanism = AmpMechanism.getInstance();

    private static NAR_XboxController controller = RobotContainer.controller;

    public static boolean climb = false;

    public static Command vibrateController(){
        return new ScheduleCommand(new StartEndCommand(()-> controller.startVibrate(), ()-> controller.stopVibrate()).withTimeout(1));
    }

    public static Command autoShoot() {
        return sequence(
            // runOnce(()-> DriverStation.reportWarning("AutoShoot: CommandStarting", false)),
            parallel(
                rampUp().withTimeout(RAMP_TIME),
                swerve.turnInPlace(false).asProxy().withTimeout(1)
                // runOnce(()-> CmdSwerveDrive.setTurnSetpoint(swerve.getTurnAngle(Robot.getAlliance() == Alliance.Red ? focalPointRed : focalPointBlue))),
                // waitUntil(()-> CmdSwerveDrive.rController.atSetpoint())
            ),
            intake.intakeRollers.outtakeNoRequirements(),
            waitSeconds(0.35),
            neutral(false)
            // runOnce(()-> DriverStation.reportWarning("AutoShoot: CommandEnding", false))
        );
    }

    public static Command rampUpAmp() {
        return sequence(
            runOnce(()-> autoAmpAlign().schedule()),
            climber.climbTo(Setpoint.AMP),
            shooter.shoot(ShooterConstants.AMP_RPM),
            waitUntil(()-> climber.atSetpoint()),
            ampMechanism.extend()
        );
    }

    public static Command ampShoot() {
        return sequence (
            climber.climbTo(Setpoint.AMP),
            shooter.shoot(ShooterConstants.AMP_RPM),
            waitUntil(()-> climber.atSetpoint()),
            ampMechanism.extend(),
            waitUntil(()-> ampMechanism.atSetpoint() && shooter.atSetpoint()),
            intake.intakeRollers.outtake(),
            waitSeconds(1.5),
            ampMechanism.retract(),
            waitUntil(()-> ampMechanism.atSetpoint()),
            neutral(false)
        );
    }

    public static Command rampRam() {
        return sequence(
            climber.climbTo(Climber.Setpoint.RAMSHOT),
            shooter.shoot(RAM_SHOT_RPM, RAM_SHOT_RPM)
        );
    }

    public static Command ramShot() {
        return sequence(
            climber.climbTo(Climber.Setpoint.RAMSHOT),
            shooter.shoot(RAM_SHOT_RPM, RAM_SHOT_RPM),
            waitUntil(()-> climber.atSetpoint() && shooter.atSetpoint()),
            intake.intakeRollers.outtakeNoRequirements(),
            waitSeconds(0.35),
            neutral(false)
        );
    }

    public static Command shoot(double rpm, double height){
        return sequence(
            // runOnce(()-> DriverStation.reportWarning("Shoot: CommandStarting", false)),
            rampUp(rpm, height).withTimeout(RAMP_TIME),
            intake.intakeRollers.outtakeNoRequirements(),
            waitSeconds(0.35),
            neutral(false)
            // runOnce(()-> DriverStation.reportWarning("Shoot: CommandEnding", false))
        );
    }

    public static Command feed(double rpm, double height){
        return sequence(
            parallel(
                // swerve.turnInPlace(()-> Robot.getAlliance() == Alliance.Blue ? 155 : 35).asProxy().withTimeout(1),
                rampUp(rpm, height)
            ),
            intake.intakeRollers.outtakeNoRequirements(),
            waitSeconds(0.1),
            neutral(false)
        );
    }

    public static Command rampUp() {
        return rampUp(ShooterConstants.MAX_RPM, ()-> climber.interpolate(swerve.getDist()));
    }

    public static Command rampUp(double rpm, double height){
        return rampUp(rpm, ()-> height);
    }

    public static Command rampUp(double rpm, DoubleSupplier height) {
        return sequence(
            climber.climbTo(height),
            shooter.shoot(rpm),
            waitUntil(()-> climber.atSetpoint() && shooter.atSetpoint())
        );
    }

    public static Command rampUpContinuous() {
        return rampUpContinuous(ShooterConstants.MAX_RPM, ()-> climber.interpolate(swerve.getPredictedDistance()));
    }

    public static Command rampUpContinuous(double rpm, DoubleSupplier height) {
        return sequence(
            shooter.shoot(rpm),
            repeatingSequence(
                climber.climbTo(height),
                waitSeconds(0.1)
            )
        );
    }

    public static Command neutral(boolean shouldStall){
        return sequence(
            // runOnce(()-> DriverStation.reportWarning("Neutral: CommandStarting", false)),
            intake.intakeRollers.runNoRequirements(0),
            shooter.setShooter(0),
            climber.climbTo(Climber.Setpoint.RETRACTED),
            waitUntil(()-> climber.atSetpoint()),
            climber.setClimber(-0.5),
            waitSeconds(0.1),
            climber.setClimber(0),
            parallel(
                // intake.retract(shouldStall),
                sequence(
                    waitSeconds(0.5),
                    climber.reset()
                )
            )
            // runOnce(()-> DriverStation.reportWarning("Neutral: CommandEnding", false))
        );
    }

    public static Command autoAmpAlign(){
        Pose2d ampPos = new Pose2d(Robot.getAlliance() == Alliance.Red ? 14.6 : 1.94, 7.75,  Rotation2d.fromDegrees(90));

        return sequence(
            runOnce(()-> Leds.getInstance().setLedColor(Colors.AMP)),
            AutoBuilder.pathfindToPose(
                ampPos,
                new PathConstraints(maxAttainableSpeed, maxAcceleration, maxAngularVelocity, maxAngularAcceleration),
                0.0, // Goal end velocity in meters/sec
                0.0 // Rotation delay distance in meters. This is how far the robot should travel before attempting to rotate.
            ),
            runOnce(()-> Leds.getInstance().setDefaultColor())
        );
    }


}