package org.joget.marketplace;

import java.util.*;
import org.joget.apps.app.lib.UserNotificationAuditTrail;
import org.joget.apps.app.model.AuditTrail;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.util.WorkflowUtil;

public class UserNotificationCustomizableAuditTrail extends UserNotificationAuditTrail {

    @Override
    public String getName() {
        return "User Notification (Customizable)";
    }

    @Override
    public String getVersion() {
        return "7.0.4";
    }

    @Override
    public String getDescription() {
        return "User Notification with customizable content for each process activity.";
    }

    @Override
    public String getLabel() {
        return "User Notification (Customizable)";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/app/userNotificationCustomizableAuditTrail.json", null, true, "messages/app/userNotificationCustomizableAuditTrail");
    }
    
    @Override
    public Object execute(Map props) {
        AuditTrail auditTrail = (AuditTrail) props.get("auditTrail");
        
        if (validation(auditTrail)) {
            String method = auditTrail.getMethod();
            Object[] args = auditTrail.getArgs();
            
            String activityInstanceId = null;
            List<String> users = null;
            
            if (method.equals("getDefaultAssignments") && args.length == 3) {
                users = (List<String>) auditTrail.getReturnObject();
                activityInstanceId = (String) args[1];
            } else if (method.equals("assignmentReassign") && args.length == 5) {
                users = new ArrayList<String> ();
                users.add((String) args[3]);
                activityInstanceId = (String) args[2];
            }
            
            if (activityInstanceId != null && !activityInstanceId.isEmpty() && users != null) {
                WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
                WorkflowActivity wfActivity = workflowManager.getActivityById(activityInstanceId);
                LogUtil.info(UserNotificationCustomizableAuditTrail.class.getName(), "Users to notify: " + users);
                
                if (wfActivity != null && !excluded((String) props.get("exclusion"), wfActivity) && !users.isEmpty()) {
                    
                    Map propsTemp = new HashMap();
                    propsTemp.putAll(props); //this is to prevent customized message to affect another assignment, thus, cloned it to customize.
                    
                    //get customized message and replace
                    Object[] customizedContent = null;
                    boolean foundMatch = false;
                    
                    if (props.get("customizedContent") instanceof Object[]){
                        customizedContent = (Object[]) props.get("customizedContent");
                    }
                    
                    if (customizedContent != null && customizedContent.length > 0) {
                        for (Object o : customizedContent) {
                            Map mapping = (HashMap) o;
                            String customActivityId = mapping.get("activityId").toString();
                                                        
                            if(customActivityId.equalsIgnoreCase(WorkflowUtil.getProcessDefIdWithoutVersion(wfActivity.getProcessDefId()) + "-" + wfActivity.getActivityDefId())){
                                //matches, obtain custom values
                                foundMatch = true;
                                String customToSpecfic = mapping.get("toSpecific").toString();
                                String customCc = mapping.get("cc").toString();
                                String customSubject = mapping.get("subject").toString();
                                String customMessage = mapping.get("emailMessage").toString();
                                String customIsHTML = mapping.get("isHtml").toString();
                                String customUrl = mapping.get("url").toString();
                                String customUrlName = mapping.get("urlName").toString();
                                String customParameterName = mapping.get("parameterName").toString();
                                String customPassoverMethod = mapping.get("passoverMethod").toString();
                                
                                WorkflowAssignment wfAssignment = workflowManager.getMockAssignment(activityInstanceId);
                                
                                if (wfAssignment != null) {
                                    String toSpecificEmail = AppUtil.processHashVariable(customToSpecfic, wfAssignment, null, null);
                                    if(!toSpecificEmail.isEmpty()){
                                        for(String user: toSpecificEmail.split(",")){
                                            users.add(toSpecificEmail.trim());
                                        }
                                    }
                                    
                                    //overrides default value
                                    propsTemp.put("cc", AppUtil.processHashVariable(customCc, wfAssignment, null, null));
                                    propsTemp.put("subject", AppUtil.processHashVariable(customSubject, wfAssignment, null, null));
                                    propsTemp.put("emailMessage", customMessage);
                                    propsTemp.put("isHtml", customIsHTML);
                                    propsTemp.put("url", customUrl);
                                    propsTemp.put("urlName", customUrlName);
                                    propsTemp.put("parameterName", customParameterName);
                                    propsTemp.put("passoverMethod", customPassoverMethod);
                                    
                                }else{
                                    
                                    //overrides default value
                                    propsTemp.put("cc", customCc);
                                    propsTemp.put("subject", customSubject);
                                    propsTemp.put("emailMessage", customMessage);
                                    propsTemp.put("isHtml", customIsHTML);
                                    propsTemp.put("url", customUrl);
                                    propsTemp.put("urlName", customUrlName);
                                    propsTemp.put("parameterName", customParameterName);
                                    propsTemp.put("passoverMethod", customPassoverMethod);
                                }
                                
                            }
                            
                        }
                    }
                    
                    String sendDefaultNotification = (String)props.get("sendDefaultNotification");
                    if(foundMatch || sendDefaultNotification.equalsIgnoreCase("true")){
                        //use parent's method to send email and push notification
                        sendEmail(propsTemp, auditTrail, workflowManager, users, wfActivity);
                    }
                    
                }
            }
        }
        
        return null;
    }
}
