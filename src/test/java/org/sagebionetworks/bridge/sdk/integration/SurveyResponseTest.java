package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.sagebionetworks.bridge.sdk.AdminClient;
import org.sagebionetworks.bridge.sdk.DeveloperClient;
import org.sagebionetworks.bridge.sdk.Roles;
import org.sagebionetworks.bridge.sdk.Session;
import org.sagebionetworks.bridge.sdk.integration.TestUserHelper.TestUser;
import org.sagebionetworks.bridge.sdk.UserClient;
import org.sagebionetworks.bridge.sdk.exceptions.BridgeSDKException;
import org.sagebionetworks.bridge.sdk.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.sdk.models.holders.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.sdk.models.holders.IdentifierHolder;
import org.sagebionetworks.bridge.sdk.models.surveys.Survey;
import org.sagebionetworks.bridge.sdk.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.sdk.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.sdk.models.surveys.SurveyResponse;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Category(IntegrationSmokeTest.class)
public class SurveyResponseTest {
    
    private static TestUser developer;
    private static TestUser user;

    private static Survey survey;
    private static GuidCreatedOnVersionHolder keys;
    
    @BeforeClass
    public static void beforeClass() {
        developer = TestUserHelper.createAndSignInUser(SurveyResponseTest.class, true, Roles.DEVELOPER);
        user = TestUserHelper.createAndSignInUser(SurveyResponseTest.class, true);

        DeveloperClient client = developer.getSession().getDeveloperClient();
        Survey testSurvey = TestSurvey.getSurvey(SurveyResponseTest.class);
        keys = client.createSurvey(testSurvey);
        client.publishSurvey(keys);

        // The API does not return all the guids for the questions as well as the survey when you 
        // save the survey. We have to get the whole survey, with questions, to get these GUIDs
        survey = client.getSurvey(keys.getGuid(), keys.getCreatedOn());
    }

    @AfterClass
    public static void deleteDeveloper() {
        if (developer != null) {
            developer.signOutAndDeleteUser();
        }
    }

    @AfterClass
    public static void deleteUser() {
        if (user != null) {
            user.signOutAndDeleteUser();
        }
    }

    @AfterClass
    public static void deleteSurvey() {
        // cleanup test survey
        Session session = TestUserHelper.getSignedInAdmin().getSession();
        AdminClient adminClient = session.getAdminClient();
        adminClient.deleteSurveyPermanently(keys);
    }

    /**
     * You don't need answers to create a survey response. You get the identifier for future answers
     * to submit. 
     */
    @Test
    public void createSurveyResponseBeforeYouHaveAnswers() {
        UserClient client = user.getSession().getUserClient();

        IdentifierHolder keys = client.submitAnswersToSurvey(survey, Lists.<SurveyAnswer>newArrayList());

        // Create a response and verify it is empty.
        SurveyResponse surveyResponse = client.getSurveyResponse(keys.getIdentifier());
        assertNotNull(surveyResponse.getIdentifier());
        assertEquals(0, surveyResponse.getSurveyAnswers().size());
        
        // Create one answer and add it to the response.
        SurveyQuestion question1 = (SurveyQuestion)survey.getElementByIdentifier("high_bp");
        List<SurveyAnswer> answers = Lists.newArrayList(question1.createAnswerForQuestion("true", "desktop"));
        
        client.addAnswersToResponse(surveyResponse.getIdentifier(), answers);
        
        // The response should be there.
        surveyResponse = client.getSurveyResponse(keys.getIdentifier());
        assertEquals("There should be one answer", 1, surveyResponse.getSurveyAnswers().size());
    }
    
    @Test
    public void submitAnswersColdForASurvey() {
        UserClient client = user.getSession().getUserClient();

        SurveyQuestion question1 = (SurveyQuestion)survey.getElementByIdentifier("high_bp");
        SurveyQuestion question2 = (SurveyQuestion)survey.getElementByIdentifier("deleuterium_dosage");
        List<SurveyAnswer> answers = Lists.newArrayList();
        answers.add(question1.createAnswerForQuestion("true", "desktop"));
        answers.add(question2.createAnswerForQuestion("5.0", "desktop"));

        IdentifierHolder keys = client.submitAnswersToSurvey(survey, answers);

        SurveyResponse surveyResponse = client.getSurveyResponse(keys.getIdentifier());
        assertEquals("There should be two answers.", 2, surveyResponse.getSurveyAnswers().size());
    }

    @Test
    public void canSubmitEveryKindOfAnswerType() {
        List<SurveyAnswer> answers = Lists.newArrayList();

        DateTime date = DateTime.parse("2014-10-30T18:33:36.081Z");
        Map<String,String> values = Maps.newHashMap();
        values.put("high_bp", "true");
        values.put("last_reading", date.toString(ISODateTimeFormat.dateTime()));
        values.put("last_checkup", date.toString(ISODateTimeFormat.date()));
        values.put("phone_number", "123-456-7890");
        values.put("deleuterium_dosage", "4.6");
        values.put("BP X DAY", "4");
        values.put("deleuterium_x_day", date.toString(ISODateTimeFormat.hourMinuteSecond()));
        values.put("time_for_appt", "PT30M");
        values.put("feeling", "[see array]"); // It's an array.

        SurveyQuestion question = (SurveyQuestion)survey.getElementByIdentifier("high_bp"); // boolean
        SurveyAnswer answer = question.createAnswerForQuestion(values.get("high_bp"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("last_reading"); // datetime
        answer = question.createAnswerForQuestion(values.get("last_reading"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("last_checkup"); // date
        answer = question.createAnswerForQuestion(values.get("last_checkup"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("phone_number"); // string
        answer = question.createAnswerForQuestion(values.get("phone_number"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("deleuterium_dosage"); // decimal
        answer = question.createAnswerForQuestion(values.get("deleuterium_dosage"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("BP X DAY"); // integer
        answer = question.createAnswerForQuestion(values.get("BP X DAY"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("deleuterium_x_day"); // time
        answer = question.createAnswerForQuestion(values.get("deleuterium_x_day"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("time_for_appt"); // duration
        answer = question.createAnswerForQuestion(values.get("time_for_appt"), "mobile");
        answers.add(answer);

        question = (SurveyQuestion)survey.getElementByIdentifier("feeling"); // duration
        answer = question.createAnswerForQuestion(Lists.newArrayList("1", "3"), "mobile");
        answers.add(answer);

        UserClient client = user.getSession().getUserClient();
        survey = client.getSurvey(keys);
        IdentifierHolder holder = client.submitAnswersToSurvey(survey, answers);

        SurveyResponse response = client.getSurveyResponse(holder.getIdentifier());

        for (SurveyAnswer savedAnswer : response.getSurveyAnswers()) {
            SurveyQuestion q = (SurveyQuestion)survey.getElementByGUID(savedAnswer.getQuestionGuid());
            String originalValue = values.get(q.getIdentifier());
            if ("[see array]".equals(originalValue)) {
                assertEquals("Answers are correct", Lists.newArrayList("1", "3"), savedAnswer.getAnswers());
            } else {
                assertEquals("Answer is correct", originalValue, savedAnswer.getAnswers().get(0));
            }
        }
    }
    
    @Test
    public void canSubmitSurveyResponseWithAnIdentifier() {
        String identifier = RandomStringUtils.randomAlphabetic(10);
        UserClient client = user.getSession().getUserClient();

        SurveyQuestion question1 = (SurveyQuestion)survey.getElementByIdentifier(TestSurvey.BOOLEAN_ID);
        SurveyQuestion question2 = (SurveyQuestion)survey.getElementByIdentifier(TestSurvey.INTEGER_ID);
        List<SurveyAnswer> answers = Lists.newArrayList();
        answers.add(question1.createAnswerForQuestion("true", "desktop"));
        answers.add(question2.createAnswerForQuestion("4", "desktop"));
        
        client.submitAnswersToSurvey(survey, identifier, answers);
        
        try {
            client.submitAnswersToSurvey(survey, identifier, answers);
            fail("Should have thrown an error");
        } catch(BridgeSDKException e) {
            assertEquals("Entity already exists HTTP status code", 409, e.getStatusCode());
        }
    }

    @Test
    public void canTriggerValidationErrors() {
        UserClient client = user.getSession().getUserClient();
        
        SurveyQuestion question1 = (SurveyQuestion)survey.getElementByIdentifier(TestSurvey.BOOLEAN_ID);
        SurveyQuestion question2 = (SurveyQuestion)survey.getElementByIdentifier(TestSurvey.INTEGER_ID);

        List<SurveyAnswer> answers = Lists.newArrayList();

        SurveyAnswer answer = question1.createAnswerForQuestion("true", "desktop");
        answers.add(answer);
        
        answer = question2.createAnswerForQuestion("44", "desktop");
        answers.add(answer);

        try {
            client.submitAnswersToSurvey(survey, answers);
            fail("Should have thrown an error");
        } catch(InvalidEntityException e) {
            assertEquals("SurveyResponse is invalid: 44 is higher than the maximum value of 8.0", e.getMessage());
        }
            
    }
    
}