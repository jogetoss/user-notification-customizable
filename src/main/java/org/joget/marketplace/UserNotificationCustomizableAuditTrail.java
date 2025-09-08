package org.joget.marketplace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.joget.apps.app.dao.UserReplacementDao;
import org.joget.apps.app.lib.EmailTool;
import org.joget.apps.app.lib.UserNotificationAuditTrail;
import org.joget.apps.app.model.AuditTrail;
import org.joget.apps.app.model.UserReplacement;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.service.PushServiceUtil;
import org.joget.commons.util.DynamicDataSourceManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.SetupManager;
import org.joget.commons.util.StringUtil;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;

public class UserNotificationCustomizableAuditTrail extends UserNotificationAuditTrail {

    @Override
    public String getName() {
        return "User Notification (Customizable)";
    }

    @Override
    public String getVersion() {
        return "8.2.4";
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
                LogUtil.info(this.getClassName(), "Users to notify: " + users);
                
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
                                    toSpecificEmail = toSpecificEmail.replace(";", ","); // add support for MS-style semi-colon (;) as a delimiter
                                    if(!toSpecificEmail.isEmpty()){
                                        for(String user: toSpecificEmail.split(",")){
                                            users.add(user.trim());
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
    
    @Override
    protected void sendEmail (final Map props, final AuditTrail auditTrail, final WorkflowManager workflowManager, final List<String> users, final WorkflowActivity wfActivity) {
        final String smtpHost = (String) props.get("host");
        
        SetupManager setupManager = (SetupManager)AppUtil.getApplicationContext().getBean("setupManager");
        String setupSmtpHost = setupManager.getSettingValue("smtpHost");
        
        if ((smtpHost != null && !smtpHost.isEmpty()) || (setupSmtpHost != null && !setupSmtpHost.isEmpty())) {
            final String profile = DynamicDataSourceManager.getCurrentProfile();            
            new PluginThread(new Runnable() {
                int retry = 0;
                public void run() {
                    WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");

                    String base = (String) props.get("base");
                    String smtpPort = (String) props.get("port");
                    String smtpUsername = (String) props.get("username");
                    String smtpPassword = (String) props.get("password");
                    String security = (String) props.get("security");

                    String decryptedSmtpPassword = smtpPassword;
                    if (smtpUsername != null && !smtpUsername.isEmpty()) {
                        if (decryptedSmtpPassword != null) {
                            decryptedSmtpPassword = SecurityUtil.decrypt(decryptedSmtpPassword);
                        }
                    }
                    
                    String from = (String) props.get("from");
                    String cc = (String) props.get("cc");
                    String bcc = (String) props.get("bcc");

                    String subject = (String) props.get("subject");
                    String emailMessage = (String) props.get("emailMessage");

                    String url = (String) props.get("url");
                    String urlName = (String) props.get("urlName");
                    String parameterName = (String) props.get("parameterName");
                    String passoverMethod = (String) props.get("passoverMethod");
                    String isHtml = (String) props.get("isHtml");
                    String p12 = (String) props.get("p12");
                    String storepass = (String) props.get("storepass");
                    String alias = (String) props.get("alias");

                    Map<String, String> replace = null;
                    if ("true".equalsIgnoreCase(isHtml)) {
                        replace = new HashMap<String, String>();
                        replace.put("\\n", "<br/>");
                    }
                    
                    String activityInstanceId = wfActivity.getId();
                    String link = getLink(base, url, passoverMethod, parameterName, activityInstanceId);
                    
                    String retryCountStr = (String) props.get("retryCount");
                    String retryIntervalStr = (String) props.get("retryInterval");
                    int retryCount = 0;
                    long retryInterval = 10000;
                    try {
                        if (retryCountStr != null && !retryCountStr.isEmpty()) {
                            retryCount = Integer.parseInt(retryCountStr);
                        }
                        if (retryIntervalStr != null && !retryIntervalStr.isEmpty()) {
                            retryInterval = Integer.parseInt(retryIntervalStr) * 1000l;
                        }
                    } catch (Exception e) {
                        LogUtil.debug(EmailTool.class.getName(), e.getLocalizedMessage());
                    }
                    
                    try {
                        for (String username : users) {
                            Collection<String> addresses = AppUtil.getEmailList(null, username, null, null);
                            if (addresses == null) {
                                addresses = new ArrayList<String>();
                            }
                            Collection<String> pushUsername = new ArrayList<String>();
                            pushUsername.add(username);
                            
                            if (!addresses.isEmpty() || !"true".equals(props.get("disablePush"))) {
                                WorkflowAssignment wfAssignment = workflowManager.getMockAssignment(activityInstanceId);
                                
                                if (wfAssignment != null) {
                                    // create the email message
                                    HtmlEmail email = AppUtil.createEmail(smtpHost, smtpPort, security, smtpUsername, smtpPassword, from, p12, storepass, alias);
                                    if (email == null) {
                                        return;
                                    }
                                    
                                    if (cc != null && cc.length() != 0) {
                                        Collection<String> ccs = AppUtil.getEmailList(null, cc, wfAssignment, auditTrail.getAppDef());
                                        for (String address : ccs) {
                                            email.addCc(StringUtil.encodeEmail(address));
                                        }
                                    }
                                    if (bcc != null && bcc.length() != 0) {
                                        Collection<String> ccs = AppUtil.getEmailList(null, bcc, wfAssignment, auditTrail.getAppDef());
                                        for (String address : ccs) {
                                            email.addBcc(StringUtil.encodeEmail(address));
                                        }
                                    }
                                    
                                    //send to replacement user
                                    UserReplacementDao urDao = (UserReplacementDao) AppUtil.getApplicationContext().getBean("userReplacementDao");
                                    String args[] = wfAssignment.getProcessDefId().split("#");
                                    Collection<UserReplacement> replaces = urDao.getUserTodayReplacedBy(username, args[0], args[2]);
                                    if (replaces != null && !replaces.isEmpty()) {
                                        for (UserReplacement ur : replaces) {
                                            pushUsername.add(ur.getReplacementUser());
                                            Collection<String> emails = AppUtil.getEmailList(null, ur.getReplacementUser(), null, null);
                                            if (emails != null && !emails.isEmpty()) {
                                                addresses.addAll(emails);
                                            }
                                        }
                                    }

                                    String emailToOutput = "";
                                    for (String address : addresses) {
                                        email.addTo(StringUtil.encodeEmail(address));
                                        emailToOutput += address + ", ";
                                    }

                                    String formattedSubject = "";
                                    if (subject != null && subject.length() != 0) {
                                        formattedSubject = WorkflowUtil.processVariable(subject, null, wfAssignment);
                                        email.setSubject(formattedSubject);
                                    }
                                    String formattedMessage = "";
                                    if (emailMessage != null && emailMessage.length() != 0) {

                                        String tempLink = link;
                                        if ("true".equalsIgnoreCase(isHtml)) {
                                            if (urlName != null && urlName.length() != 0) {
                                                tempLink = "<a href=\"" + link + "\">" + urlName + "</a>";
                                            } else {
                                                tempLink = "<a href=\"" + link + "\">" + link + "</a>";
                                            }
                                            String tempEmailMessage = emailMessage;
                                            if (emailMessage.contains("#assignment.link#")) {
                                                tempEmailMessage = tempEmailMessage.replaceAll(StringUtil.escapeRegex("#assignment.link#"), StringUtil.escapeRegex(tempLink));
                                            } else {
                                                tempEmailMessage = emailMessage + "<br/><br/><br/>" + tempLink;
                                            }
                                            formattedMessage = AppUtil.processHashVariable(tempEmailMessage, wfAssignment, null, replace);
                                            
                                            //TODO: consider replace the next line to platform-wise support parsing of hash variables in the value returned by another hash variable
                                            formattedMessage = AppUtil.processHashVariable(formattedMessage, wfAssignment, null, replace);
                                            
                                            email.setHtmlMsg(formattedMessage);
                                        } else {
                                            String tempEmailMessage = emailMessage;
                                            if (emailMessage.contains("#assignment.link#")) {
                                                tempEmailMessage = tempEmailMessage.replaceAll(StringUtil.escapeRegex("#assignment.link#"), StringUtil.escapeRegex(tempLink));
                                            } else {
                                                tempEmailMessage = emailMessage + "\n\n\n" + tempLink;
                                            }
                                            formattedMessage = AppUtil.processHashVariable(tempEmailMessage, wfAssignment, null, replace);
                                            
                                            //TODO: consider replace the next line to platform-wise support parsing of hash variables in the value returned by another hash variable
                                            formattedMessage = AppUtil.processHashVariable(formattedMessage, wfAssignment, null, replace);
                                            
                                            email.setMsg(formattedMessage);
                                        }
                                    }
                                    email.setCharset("UTF-8");
                                    
                                    if (!addresses.isEmpty()) {
                                        AppUtil.emailAttachment(props, wfAssignment, auditTrail.getAppDef(), email);

                                        try {
                                            LogUtil.info(UserNotificationCustomizableAuditTrail.class.getName(), "Sending email from=" + email.getFromAddress().toString() + " to=" + emailToOutput + "cc=" + cc + ", bcc=" + bcc + ", subject=" + email.getSubject());
                                            email.send();
                                            LogUtil.info(UserNotificationCustomizableAuditTrail.class.getName(), "Sending email completed for subject=" + email.getSubject());
                                        } catch (EmailException ex) {
                                            LogUtil.error(UserNotificationCustomizableAuditTrail.class.getName(), ex, "Error sending email");
                                            while (retry < retryCount) {
                                                retry++;
                                                try {
                                                    LogUtil.info(UserNotificationCustomizableAuditTrail.class.getName(), "Sending email attempt " + retry + " after " + (retryInterval/1000) + " seconds");
                                                    Thread.sleep(retryInterval);
                                                } catch (Exception e) {}

                                                try {
                                                    LogUtil.info(UserNotificationCustomizableAuditTrail.class.getName(), "Sending email attempt " + retry + ": Sending email from=" + email.getFromAddress().toString() + ", to=" + emailToOutput + "cc=" + cc + ", bcc=" + bcc + ", subject=" + email.getSubject());
                                                    email.sendMimeMessage();
                                                    LogUtil.info(UserNotificationCustomizableAuditTrail.class.getName(), "Sending email attempt " + retry + " completed for subject=" + email.getSubject());
                                                    break;
                                                } catch (EmailException ex2) {
                                                    LogUtil.error(UserNotificationCustomizableAuditTrail.class.getName(), ex, "Sending email attempt " + retry + " failure.");
                                                }
                                            }
                                        }
                                    }

                                    if (!"true".equals(props.get("disablePush"))) {
                                        for (String u : pushUsername) {
                                            PushServiceUtil.sendUserPushNotification(u, formattedSubject, formattedMessage, link, "", "", true);
                                        }
                                    }
                                } else {
                                    LogUtil.debug(UserNotificationCustomizableAuditTrail.class.getName(), "Fail to retrieve assignment for " + username);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LogUtil.error(UserNotificationCustomizableAuditTrail.class.getName(), e, "Error executing plugin");
                    }
                }
            }).start();
            
        } else {
            LogUtil.info(this.getClassName(), "SMTP Host is not configured.");
        }
    }
}
