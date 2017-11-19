package com.example.bot.spring;

import java.util.*;

import java.io.IOException;

import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.Date;
import java.util.TimeZone;
import com.linecorp.bot.model.profile.UserProfileResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.io.ByteStreams;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.event.BeaconEvent;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.JoinEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.UnfollowEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.LocationMessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.message.AudioMessage;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.ImagemapMessage;
import com.linecorp.bot.model.message.LocationMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.imagemap.ImagemapArea;
import com.linecorp.bot.model.message.imagemap.ImagemapBaseSize;
import com.linecorp.bot.model.message.imagemap.MessageImagemapAction;
import com.linecorp.bot.model.message.imagemap.URIImagemapAction;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
public class CouponWarehouse{
  final private static int NUMOFCOUPONS = 5000;
  final private static int NUMOFCODES = 5000;
  private static ArrayList<String> existingUids;
  private static ArrayList<String> newUids = new ArrayList<String>();
  private static ArrayList<String> codes = new ArrayList<String>();
  private static ArrayList<Coupon> coupons = new ArrayList<Coupon>();
  private static int couponsRemaining = NUMOFCOUPONS;
  private static boolean started = false;
  private static ArrayList<String> uids;
  private static CouponWarehouse couponWarehouse = new CouponWarehouse();

  private static void generateRandomCode(){
    for(int i = 0; i < NUMOFCODES; i++) {
      String code = "";
      do{
        for(int j = 0; j < 6 ; j++) {
          Random rand = new Random();
          int rn = rand.nextInt(3) + 1;
          int n = 0;
          switch(rn) {
            case 1:n = rand.nextInt(10)+48; break;
            case 2:n = rand.nextInt(26)+65; break;
            case 3:n = rand.nextInt(26)+97; break;
          }
          code += (char)n;
        }
      }while(codes.contains(code));
      codes.add(code);
    }
  }
  private static void fetchUsers(){
    SQLDatabaseEngine db = new SQLDatabaseEngine();
    existingUids = db.fetchUIDs();
  }
  private CouponWarehouse(){
    generateRandomCode();
    fetchUsers();
  }
  static public CouponWarehouse getInstance(){
    return couponWarehouse;
  }
  static public MsgAttachedData<Date> startCampaign(){
    String msg = "Campaign has been started!\n "
    +"Each current user can type \"friend\" into the chatbot and the chatbot will reply them a 6-digits unique code. "
    + "The user can give this code to his friend and recommend them to add the chatbot as their line friend. "
    + "The new user then type \"code\" into the chatbot followed by the 6-digits unique code, then the chatbot will send an ice-cream store electronic coupon to both of the new user and the old user. "
    + "Each new user can claim the coupon once only. Each user can recommend infinite number of friends. "
    + "Each new user can also recommend new users. Users who registered before the campaign cannot type \"code\" to get the coupon. "
    + "After 5000 copies of ice-cream coupon were given out, the campaign stops.";
    Date now = new Date();
    started = true;
    return new MsgAttachedData<Date>(msg,now);
  }

  public void register(Users obj) {
      String uid = obj.getID();
  		if ( !newUids.contains(uid) ) newUids.add(uid);
  }

  public void unregister(Users obj) {
      String uid = obj.getID();
      existingUids.remove(uid);
  		newUids.remove(uid);
  }

  public MsgAttachedData<ArrayList<String>> getNotifiableObservers() {
      String msg = "Someone has invited their firends and got coupon!\n"
           +"We have " + Integer.toString(couponsRemaining) + " coupons left!\n"
           +"Go invite friends and enjoy ice creams!";
      ArrayList<String> allUids = new ArrayList<String>(existingUids);
      allUids.addAll(newUids);
   		return new MsgAttachedData<ArrayList<String>>(msg, allUids);
  }

  public String issueCode(String inviter) {
    for(Coupon c:coupons){
      if(c != null)
        if(c.getInviter().equals(inviter))
          return c.getCode();
    }

	  Random rand = new Random();
	  int n = rand.nextInt(codes.size());
	  //assert(!(n >= 0 && n < codes.size()));
	  String code = codes.get(n);
    coupons.add(new Coupon(inviter,code));
    codes.remove(code);

    return code;
  }
  public Coupon issueCoupon(String invitee, String code){
    if (isCouponRemaining()){
      couponsRemaining--;

      int i = 0;
      boolean found = false;
      for(Coupon c : coupons){
        if(c.getCode().equals(code)) {found = true; break;}
        else i++;
      }
      if(found){
       coupons.get(i).setInvitee(invitee);
       if( ! isNewUser(coupons.get(i).getInviter()) ) couponsRemaining--;
       //log.info("X!#R@#%#$^%@$&U^%IJ^*(K%HE$YG#Q$WTC$QRC#)RC<@#_R(!#<_CR*@＃)");
       return coupons.get(i);
     }
     return null;
    }
    return null;
  }
  public boolean isCodeValid(String code){
    for(Coupon c : coupons){
      if(c.getCode().equals(code)) {return true;}
    }
    return false;
  }
  public boolean isCouponRemaining(){
    return (couponsRemaining > 0);
  }
  public boolean isNewUser(Users user){
    String uid = user.getID();
    return isNewUser(uid);
  }
  public boolean isNewUser(String uid){
    return newUids.contains(uid);
  }
  public boolean canGetCouponFromCode(Users user){
    if (isNewUser(user)){
      return haveNotGotCouponYet(user);
    }
    else return false;
  }
  public boolean checkSelf(String uid){
    for(Coupon c : coupons){
      if(c.getInviter().equals(uid)) return false;
    }
    return true;
  }
  public static boolean isCampaignStarted(){
    return started;
  }
  public boolean haveNotGotCouponYet(Users user){
    String uid = user.getID();
    for(Coupon c:coupons){
      if (c.getInvitee() == uid) return false;
      else if(c.getInviter() == uid && c.getInvitee() != null) return false;
    }
    return true;
  }
}
