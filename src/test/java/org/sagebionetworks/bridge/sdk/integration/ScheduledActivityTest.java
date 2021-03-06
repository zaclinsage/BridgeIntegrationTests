package org.sagebionetworks.bridge.sdk.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;

import org.sagebionetworks.bridge.sdk.integration.TestUserHelper.TestUser;
import org.sagebionetworks.bridge.rest.api.ForConsentedUsersApi;
import org.sagebionetworks.bridge.rest.api.SchedulesApi;
import org.sagebionetworks.bridge.rest.model.Activity;
import org.sagebionetworks.bridge.rest.model.ActivityType;
import org.sagebionetworks.bridge.rest.model.Role;
import org.sagebionetworks.bridge.rest.model.Schedule;
import org.sagebionetworks.bridge.rest.model.SchedulePlan;
import org.sagebionetworks.bridge.rest.model.ScheduleStatus;
import org.sagebionetworks.bridge.rest.model.ScheduleType;
import org.sagebionetworks.bridge.rest.model.ScheduledActivity;
import org.sagebionetworks.bridge.rest.model.ScheduledActivityList;
import org.sagebionetworks.bridge.rest.model.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.rest.model.TaskReference;

@Category(IntegrationSmokeTest.class)
public class ScheduledActivityTest {
    
    private TestUser user;
    private TestUser developer;
    private SchedulesApi schedulePlansApi;
    private ForConsentedUsersApi usersApi;

    @Before
    public void before() throws Exception {
        developer = TestUserHelper.createAndSignInUser(ScheduledActivityTest.class, true, Role.DEVELOPER);
        user = TestUserHelper.createAndSignInUser(ScheduledActivityTest.class, true);

        schedulePlansApi = developer.getClient(SchedulesApi.class);
        usersApi = user.getClient(ForConsentedUsersApi.class);
        
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule 1");
        schedule.setDelay("P3D");
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setTimes(Lists.newArrayList("10:00"));
        
        TaskReference taskReference = new TaskReference();
        taskReference.setIdentifier("task:AAA");
        
        Activity activity = new Activity();
        activity.setLabel("Activity 1");
        activity.setTask(taskReference);
        
        schedule.setActivities(Lists.newArrayList(activity));

        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        strategy.setType("SimpleScheduleStrategy");
        
        SchedulePlan plan = new SchedulePlan();
        plan.setLabel("Schedule plan 1");
        plan.setStrategy(strategy);
        schedulePlansApi.createSchedulePlan(plan).execute();
        
        // Add a schedule plan in the future... this should not effect any tests, *until* we request
        // a minimum number of tasks, which will retrieve this.
        schedule = new Schedule();
        schedule.setLabel("Schedule 2");
        schedule.setDelay("P1M");
        schedule.setInterval("P1M");
        schedule.setExpires("P3W");
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setTimes(Lists.newArrayList("10:00"));
        
        taskReference = new TaskReference();
        taskReference.setIdentifier("task:BBB");
        
        activity = new Activity();
        activity.setLabel("Activity 2");
        activity.setTask(taskReference);
        schedule.setActivities(Lists.newArrayList(activity));
        
        strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        strategy.setType("SimpleScheduleStrategy");
        
        plan = new SchedulePlan();
        plan.setLabel("Schedule plan 2");
        plan.setStrategy(strategy);
        schedulePlansApi.createSchedulePlan(plan).execute();
    }

    @After
    public void after() throws Exception {
        try {
            List<SchedulePlan> plans = schedulePlansApi.getSchedulePlans().execute().body().getItems();
            for (SchedulePlan plan : plans) {
                schedulePlansApi.deleteSchedulePlan(plan.getGuid()).execute();
            }
        } finally {
            if (developer != null) {
                developer.signOutAndDeleteUser();
            }
            if (user != null) {
                user.signOutAndDeleteUser();
            }
        }
    }
    
    @Test
    public void createSchedulePlanGetScheduledActivities() throws Exception {
        ScheduledActivityList scheduledActivities = usersApi.getScheduledActivities("+00:00", 4, 0).execute().body();
        
        ScheduledActivity schActivity = scheduledActivities.getItems().get(0);
        assertEquals(ScheduleStatus.SCHEDULED, schActivity.getStatus());
        assertNotNull(schActivity.getScheduledOn());
        assertNull(schActivity.getExpiresOn());
        
        Activity activity = schActivity.getActivity();
        assertEquals(ActivityType.TASK, activity.getActivityType());
        assertEquals("Activity 1", activity.getLabel());
        assertEquals("task:AAA", activity.getTask().getIdentifier());

        schActivity.setStartedOn(DateTime.now());
        usersApi.updateScheduledActivities(scheduledActivities.getItems()).execute();
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 3, null).execute().body();

        assertEquals((Integer)1, scheduledActivities.getTotal());
        assertEquals(ScheduleStatus.STARTED, scheduledActivities.getItems().get(0).getStatus());
        
        schActivity = scheduledActivities.getItems().get(0);
        schActivity.setFinishedOn(DateTime.now());
        usersApi.updateScheduledActivities(scheduledActivities.getItems()).execute();
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 3, null).execute().body();
        assertEquals((Integer)0, scheduledActivities.getTotal()); // no activities == finished
    }
    
    @Test
    public void getScheduledActivitiesWithMinimumActivityValue() throws Exception {
        ScheduledActivityList scheduledActivities = usersApi.getScheduledActivities("+00:00", 4, 2).execute().body();
        
        Multiset<String> idCounts = getMultiset(scheduledActivities);
        assertEquals(1, idCounts.count("task:AAA"));
        assertEquals(2, idCounts.count("task:BBB"));
        
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 4, 0).execute().body();
        idCounts = getMultiset(scheduledActivities);
        assertEquals(1, idCounts.count("task:AAA"));
        assertEquals(0, idCounts.count("task:BBB"));
        
        scheduledActivities = usersApi.getScheduledActivities("+00:00", 4, 5).execute().body();
        idCounts = getMultiset(scheduledActivities);
        assertEquals(1, idCounts.count("task:AAA"));
        assertEquals(5, idCounts.count("task:BBB"));
    }
    
    private Multiset<String> getMultiset(ScheduledActivityList scheduledActivities) {
        return HashMultiset.create(scheduledActivities.getItems().stream()
                .map((act) -> act.getActivity().getTask().getIdentifier())
                .collect(Collectors.toList()));
    }
    
}
