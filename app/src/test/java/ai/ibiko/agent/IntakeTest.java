package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ai.ibiko.agent.Intake.Kind;

/** A message sent while a job runs goes to the right place: only a change to the project reaches the plan. */
public class IntakeTest {
    static final String JOB = "Run a deep audit of the Dvine-ai-frontend codebase and write the report as a PDF";

    @Test
    public void questionsAndErrandsNeverReachThePlan() {
        assertEquals(Kind.QUESTION, Intake.sort("how far are you with the audit?", JOB));
        assertEquals(Kind.QUESTION, Intake.sort("What does the SteerInbox class do", JOB));
        assertEquals(Kind.QUESTION, Intake.sort("is the report going to be long?", JOB));
        assertEquals(Kind.REQUEST, Intake.sort("generate a birthday background photo", JOB));
        assertEquals(Kind.REQUEST, Intake.sort("send my mum a happy birthday message", JOB));
        assertEquals(Kind.CHAT, Intake.sort("thanks", JOB));
        assertEquals(Kind.CHAT, Intake.sort("Okay boss!", JOB));
    }

    @Test
    public void howToWorkIsANoteNotAPlanChange() {
        assertEquals(Kind.STEER, Intake.sort("focus on the api folder first", JOB));
        assertEquals(Kind.STEER, Intake.sort("skip the tests for now", JOB));
        assertEquals(Kind.STEER, Intake.sort("don't push anything yet", JOB));
        assertEquals(Kind.STEER, Intake.sort("please hurry", JOB));
    }

    @Test
    public void ideasForTheProjectGoIntoThePlan() {
        assertEquals(Kind.PLAN_CHANGE, Intake.sort("also check the login code for security holes", JOB));
        assertEquals(Kind.PLAN_CHANGE, Intake.sort("add a section on test coverage to the report", JOB));
        assertEquals(Kind.PLAN_CHANGE, Intake.sort("can you also review the gradle build files?", JOB));
        assertEquals(Kind.PLAN_CHANGE, Intake.sort("it should list every crash it finds in the audit", JOB));
        assertEquals(Kind.PLAN_CHANGE, Intake.sort("what if we add a summary page to the PDF", JOB));
        assertEquals(Intake.PLAN + "add x", Intake.forJob(Kind.PLAN_CHANGE, "add x"));
        assertEquals(Intake.STEER + "skip y", Intake.forJob(Kind.STEER, "skip y"));
    }
}
