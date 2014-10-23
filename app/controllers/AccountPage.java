package controllers;

import com.antigenomics.vdjtools.Software;
import com.avaje.ebean.Ebean;
import models.Account;
import models.LocalUser;
import models.UserFile;
import play.Logger;
import play.Play;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import securesocial.core.Identity;
import securesocial.core.java.SecureSocial;
import utils.CommonUtil;
import utils.ComputationUtil;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@SecureSocial.SecuredAction
public class AccountPage extends Controller {


    public static Result index() {

        /**
         * Identifying User using the SecureSocial API
         * and render the account page
         */
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        return ok(views.html.account.accountMainPage.render(fileForm, localUser.account));
    }

    private static Form<UserFile> fileForm = Form.form(UserFile.class);


    public static Result newFile() {

        /**
         * Identifying User using the SecureSocial API
         */

        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;

        /**
         * Render addfile.scala.html file
         */

        if (account != null) {
            return ok(views.html.account.addFile.render(fileForm, account));
        }
        return redirect(routes.AccountPage.index());
    }



    public static Result saveNewFile() {

        /**
         * Identifying user using the SecureSocial API
         */
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;

        if (account == null) {
            flash("error", "Account does not exist");
            return redirect(routes.Application.index());
        }
        Logger.of("user." + account.userName).info("User" + account.userName + " is uploading new file");

        /**
         * Checking files count
         */

        if (account.userfiles.size() >= 10) {
            flash("info", "You have exceeded the limit of the number of files");
            Logger.of("user." + account.userName).info("User" + account.userName + " exceeded  the limit of the number of files");
            return Results.redirect(routes.AccountPage.index());
        }

        /**
         * Getting boundForm from request
         */

        Form<UserFile> boundForm = fileForm.bindFromRequest();
        if (boundForm.hasErrors()) {
            flash("error", "Please correct the form below.");
            return ok(views.html.account.addFile.render(fileForm, account));
        }

        /**
         * Getting the file from request body
         */

        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart file = body.getFile("file");

        if (file == null) {
            flash("error", "You should upload file");
            return ok(views.html.account.addFile.render(fileForm, account));
        }

        /**
         * Getting fileName
         * If User do not enter the name of file
         * it will be fills automatically using current file name
         */

        String fileName = null;
        if (boundForm.get().fileName.equals("")) {
            fileName = FilenameUtils.removeExtension(file.getFilename());
        } else {
            fileName = boundForm.get().fileName;
        }

        String pattern = "^[a-zA-Z0-9_.-]{1,20}$";
        if (!fileName.matches(pattern)) {
            flash("error","Invalid name, you should use only letters and numbers");
            return ok(views.html.account.addFile.render(fileForm, account));
        }


        List<UserFile> allFiles = UserFile.findByAccount(account);
        for (UserFile userFile: allFiles) {
            if (userFile.fileName.equals(fileName)) {
                flash("error", "You should use unique names for your files");
                return ok(views.html.account.addFile.render(fileForm, account));
            }
        }

        /**
         * Verification of the existence the account and the file
         */

        try {

            /**
             * Creation the UserFile class
             */

            File uploadedFile = file.getFile();
            String unique_name = CommonUtil.RandomStringGenerator.generateRandomString(30, CommonUtil.RandomStringGenerator.Mode.ALPHA);
            File fileDir = (new File(account.userDirPath + "/" + unique_name + "/"));

            /**
             * Trying to create file's directory
             * if failed redirect to the account
             */

            if (!fileDir.exists()) {
                Boolean created = fileDir.mkdir();
                if (!created) {
                    flash("error","Error while adding file");
                    Logger.of("user." + account.userName).error("Error creating file directory for user " + account.userName);
                    return Results.redirect(routes.AccountPage.index());
                }
            }

            /**
             * Checking
             */

            Boolean uploaded = uploadedFile.renameTo(new File(account.userDirPath + "/" + unique_name + "/file"));
            if (!uploaded) {
                flash("error", "Error while adding file");
                Logger.of("user." + account.userName).error("Error upload file for user " + account.userName);
                return Results.redirect(routes.AccountPage.index());
            }

            UserFile newFile = new UserFile(account, fileName,
                    unique_name, boundForm.get().softwareTypeName,
                    account.userDirPath + "/" + unique_name + "/file",
                    fileDir.getAbsolutePath());

            /**
             * Database updating with relationships
             * UserFile <-> Account
             */


            Ebean.save(newFile);
            account.userfiles.add(newFile);
            Ebean.update(account);
            return Results.redirect(routes.Computation.computationPage(fileName));
        } catch (Exception e) {
            flash("error", "Error while adding file");
            Logger.of("user." + account.userName).error("Error while uploading new file for user : " + account.userName);
            return Results.redirect(routes.AccountPage.index());
        }
    }


    public static Result deleteFile(String fileName) {

        /**
         * Identifying User using the SecureSocial API
         */

        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;

        Logger.of("user." + account.userName).info("User " + account.userName + "is trying to delete file named " + fileName);
        UserFile file = UserFile.fyndByNameAndAccount(account, fileName);

        if (file == null) {
            flash("error", "You have no files named " + fileName);
            Logger.of("user." + account.userName).error("User " + account.userName +"have no file named " + fileName);
            return ok(views.html.account.accountMainPage.render(fileForm, account));
        }

        /**
         * Getting file directory
         * and try to delete it
         */

        String fileDirectoryName =  account.userDirPath + "/" + file.uniqueName;

        File f = new File(fileDirectoryName + "/file");
        File histogram = new File(fileDirectoryName + "/histogram.cache");
        File histogramV = new File(fileDirectoryName + "/histogramV.cache");
        File vdjUsage = new File(fileDirectoryName + "/vdjUsage.cache");
        File annotation = new File(fileDirectoryName + "/annotation.cache");
        File basicStats = new File(fileDirectoryName + "/basicStats.cache");
        File diversity = new File(fileDirectoryName + "/diversity.cache");
        File fileDir = new File(fileDirectoryName + "/");
        Boolean deleted = false;
        try {
            f.delete();
            annotation.delete();
            basicStats.delete();
            histogram.delete();
            histogramV.delete();
            vdjUsage.delete();
            diversity.delete();
            if (fileDir.delete()) {
                deleted = true;
            }
        } catch (Exception e) {
            Logger.of("user." + account.userName).error("User: " + account.userName + "Error while deleting file " + fileName);
            e.printStackTrace();
        }
        if (deleted) {
            account.userfiles.remove(file);
            Ebean.delete(file);
            Ebean.update(account);
            Logger.of("user." + account.userName).info("User " + account.userName + " successfully deleted file named " + fileName);
            return Results.redirect(routes.AccountPage.index());
        } else {
            flash("error", "Error deleting file");
            Logger.of("user." + account.userName).error("User: " + account.userName + "Error while deleting file " + fileName);
            return Results.redirect(routes.AccountPage.index());
        }
    }

    public static Result deleteAll() {
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;
        for (UserFile file: account.userfiles) {
            deleteFile(file.fileName);
        }
        return Results.redirect(routes.AccountPage.index());
    }


    public static Result fileInformation(String fileName) {

        /**
         * Identifying User using the SecureSocial API
         */

        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;


        UserFile file = UserFile.fyndByNameAndAccount(account, fileName);

        /**
         * Verifying access to the file
         * if file belong to User redirect
         * to file information page
         * else redirect to the account page
         */

        if (account.userfiles.contains(file)) {
            return ok(views.html.computation.fileComputationResults.render(account, file));
        } else {
            return redirect(routes.AccountPage.index());
        }
    }

    public static Result fileUpdatePage(String fileName) {
        /**
         * Identifying User using the SecureSocial API
         */
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;
        UserFile file = UserFile.fyndByNameAndAccount(account, fileName);
        if (!file.rendering) {
            return ok(views.html.account.fileUpdate.render(Form.form(UserFile.class).fill(file), account, fileName));
        } else {
            return redirect(routes.AccountPage.index());
        }
    }

    public static Result fileUpdate(String fileName) {

        /**
         * Identifying User using the SecureSocial API
         */

        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;

        if (account == null) {
            flash("error", "Account does not exist");
            return redirect(routes.Application.index());
        }

        UserFile file = UserFile.fyndByNameAndAccount(account, fileName);

        if (file == null) {
            flash("error", "You have no file named " + fileName);
            Logger.of("user." + account.userName).error("Update error: User " + account.userName + " have no file named " + fileName);
            return ok(views.html.account.accountMainPage.render(fileForm, account));
        }

        if (account.userfiles.contains(file)) {
            Form<UserFile> boundForm = fileForm.bindFromRequest();
            if (boundForm.hasErrors()) {
                flash("error", "Please correct the form below.");
                return ok(views.html.account.addFile.render(fileForm, account));
            }

        } else {
            Logger.of("user." + account.userName).error("Update error: User " + account.userName + " is trying to update another's file");
            return ok("access restricted");
        }

        if (!file.rendering) {
            Http.MultipartFormData body = request().body().asMultipartFormData();
            Http.MultipartFormData.FilePart newFile = body.getFile("file");
            Form<UserFile> boundForm = fileForm.bindFromRequest();
            file.fileName = boundForm.get().fileName;
            file.softwareTypeName = boundForm.get().softwareTypeName;
            file.softwareType = Software.byName(boundForm.get().softwareTypeName);
            file.rendered = false;
            Ebean.update(file);
            if (newFile != null) {
                File nf = new File(file.fileDirPath + "/file");
                nf.delete();
                Boolean uploaded = newFile.getFile().renameTo(new File(account.userDirPath + "/" + file.uniqueName + "/file"));
                if (!uploaded) {
                    flash("error", "Error while adding file");
                    Logger.of("user." + account.userName).error("Update error: User " + account.userName + ": failed to upload new file");
                    return Results.redirect(routes.AccountPage.index());
                }

            }
            Logger.of("user." + account.userName).info("Update file: User " + account.userName + " succesfully updated file named " + fileName);
            return redirect(routes.Computation.computationPage(file.fileName));
        } else {
            return redirect(routes.AccountPage.index());
        }
    }

    public static Result basicStats() {
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;

        if (account!=null) {
            return ok(views.html.computation.basicStats.render(account));
        } else {
            return redirect(routes.Application.index());
        }
    }


    public static Result diversityPage() {
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;
        if (account!=null) {
            return ok(views.html.computation.diversityStats.render(account));
        } else {
            return redirect(routes.Application.index());
        }
    }

    public static Result asyncUploadFiles() {
        /**
         * Identifying user using the SecureSocial API
         */
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;

        HashMap<String, String> resultJson = new HashMap<>();

        if (account == null) {
            resultJson.put("error", "Unknow Error");
            return ok(Json.toJson(resultJson));
        }
        Logger.of("user." + account.userName).info("User " + account.userName + " is uploading new file");

        /**
         * Checking files count
         */

        if (account.userfiles.size() >= 10) {
            resultJson.put("error", "You have exceeded the limit of the number of files");
            Logger.of("user." + account.userName).info("User" + account.userName + " exceeded  the limit of the number of files");
            return ok(Json.toJson(resultJson));
        }

        /**
         * Getting the file from request body
         */

        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart file = body.getFile("files[]");

        if (file == null) {
            resultJson.put("error", "You should upload file");
            return ok(Json.toJson(resultJson));
        }

        /**
         * Getting fileName
         * If User do not enter the name of file
         * it will be fills automatically using current file name
         */

        String fileName = null;
        //TODO
        if (file.getFilename().equals("")) {
            fileName = FilenameUtils.removeExtension(file.getFilename());
        } else {
            fileName = body.asFormUrlEncoded().get("fileName")[0];
        }

        String pattern = "^[a-zA-Z0-9_.-]{1,20}$";
        if (!fileName.matches(pattern)) {
            resultJson.put("error", "Invalid name");
            return ok(Json.toJson(resultJson));
        }


        List<UserFile> allFiles = UserFile.findByAccount(account);
        for (UserFile userFile: allFiles) {
            if (userFile.fileName.equals(fileName)) {
                resultJson.put("error", "You should use unique names for your files");
                return ok(Json.toJson(resultJson));
            }
        }

        /**
         * Verification of the existence the account and the file
         */

        try {

            /**
             * Creation the UserFile class
             */

            File uploadedFile = file.getFile();
            String unique_name = CommonUtil.RandomStringGenerator.generateRandomString(30, CommonUtil.RandomStringGenerator.Mode.ALPHA);
            File fileDir = (new File(account.userDirPath + "/" + unique_name + "/"));

            /**
             * Trying to create file's directory
             * if failed redirect to the account
             */

            if (!fileDir.exists()) {
                Boolean created = fileDir.mkdir();
                if (!created) {
                    resultJson.put("error", "Server currently unavailable");
                    Logger.of("user." + account.userName).error("Error creating file directory for user " + account.userName);
                    return ok(Json.toJson(resultJson));
                }
            }

            /**
             * Checking
             */

            Boolean uploaded = uploadedFile.renameTo(new File(account.userDirPath + "/" + unique_name + "/file"));
            if (!uploaded) {
                resultJson.put("error", "Server currently unavailable");
                Logger.of("user." + account.userName).error("Error upload file for user " + account.userName);
                return ok(Json.toJson(resultJson));
            }

            UserFile newFile = new UserFile(account, fileName,
                    unique_name, body.asFormUrlEncoded().get("softwareTypeName")[0],
                    account.userDirPath + "/" + unique_name + "/file",
                    fileDir.getAbsolutePath());

            /**
             * Database updating with relationships
             * UserFile <-> Account
             */

            Ebean.save(newFile);
            account.userfiles.add(newFile);
            Ebean.update(account);
            resultJson.put("success", "success");
            return ok(Json.toJson(resultJson));
        } catch (Exception e) {
            resultJson.put("error", "Unknown error");
            e.printStackTrace();
            Logger.of("user." + account.userName).error("Error while uploading new file for user : " + account.userName);
            return ok(Json.toJson(resultJson));
        }
    }

    public static Result getAccountAllFilesInformation() {
        Identity user = (Identity) ctx().args.get(SecureSocial.USER_KEY);
        LocalUser localUser = LocalUser.find.byId(user.identityId().userId());
        Account account = localUser.account;

        List<HashMap<String, Object>> fileNames = new ArrayList<>();
        for (UserFile file: account.userfiles) {
            HashMap<String, Object> fileInformation = new HashMap<>();
            fileInformation.put("fileName", file.fileName);
            fileInformation.put("softwareTypeName", file.softwareTypeName);
            fileInformation.put("rendered", file.rendered);
            fileInformation.put("rendering", file.rendering);
            fileInformation.put("renderCount", file.renderCount);
            fileNames.add(fileInformation);
        }
        return ok(Json.toJson(fileNames));
    }

}
