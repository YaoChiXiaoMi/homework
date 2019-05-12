package com.youwen.runnable;

import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.youwen.bean.CaiJi;
import com.youwen.bean.DabaoResultdate;
import com.youwen.bean.RWSign;
import com.youwen.bean.Timing;
import com.youwen.bean.writeobj;
import com.youwen.callback.ModCallBack;
import com.youwen.conf.XTconf;
import com.youwen.data.Swritedatas;
import com.youwen.data.Zwritedatas;
import com.youwen.jiekou.ModCaiJiCallBack;
import com.youwen.jiekou.MyThread;
import com.youwen.jiekou.RWBaoCallBack;
import com.youwen.tools.flieIO;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class ManagementThread_T
  implements MyThread, Runnable
{
  private BaoCallBack BaoCallBackObj = new BaoCallBack(null);
  private ModCaiJiCallBack ModCallBackObj = new ModCallBack();
  private Map<String, ArrayList<CaiJi>> deviceCaijis = new HashMap();
  private Map<String, ArrayList<Timing>> Timings = new HashMap();
  private Map<String, ArrayList<RWSign>> RWSigns = new HashMap();
  private Map<String, CaijiRunable_T> CaijiRunable_Ts = new HashMap();
  private Map<String, Integer> Link_Decnum = new HashMap();
  private Map<String, JSONArray> DecJSONArrays = new HashMap();
  private ExecutorService Threadpool = null;
  private boolean isclose = false;
  private int Linknum = 0;
  private Timer timer = new Timer();
  private static ManagementThread_T singleton;
  
  public static synchronized ManagementThread_T getInstance()
  {
    if (singleton == null) {
      synchronized (ManagementThread_T.class)
      {
        if (singleton == null) {
          singleton = new ManagementThread_T();
        }
      }
    }
    return singleton;
  }
  
  public void run()
  {
    if (this.isclose)
    {
      final Set<String> LinkNames = this.deviceCaijis.keySet();
      this.timer.scheduleAtFixedRate(new TimerTask()
      {
        public void run()
        {
          for (String LinkName : LinkNames) {
            ManagementThread_T.this.TimeCalculation(LinkName);
          }
        }
      }, 1000L, 10L);
      flieIO.LOG(XTconf.getRizhiDizhi(), "????????????");
    }
    else
    {
      flieIO.LOG(XTconf.getRizhiDizhi(), "??????????????????????????????????????");
    }
  }
  
  public void Thclose() {}
  
  private void TimeCalculation(String linkName)
  {
    ArrayList<CaiJi> caiJis = (ArrayList)this.deviceCaijis.get(linkName);
    ArrayList<Timing> Timing = (ArrayList)this.Timings.get(linkName);
    ArrayList<RWSign> RWSign = (ArrayList)this.RWSigns.get(linkName);
    if ((caiJis.size() == Timing.size()) && (Timing.size() == RWSign.size())) {
      for (int i = 0; i < Timing.size(); i++)
      {
        Timing Timingobj = (Timing)Timing.get(i);
        RWSign RWSignObj = (RWSign)RWSign.get(i);
        CaiJi CaiJiObj = (CaiJi)caiJis.get(i);
        RWtimedeal(linkName, Integer.valueOf(i), Timingobj, RWSignObj, CaiJiObj);
        SWdatadeal(linkName, CaiJiObj);
        RWovertimedeal(Timingobj, RWSignObj, CaiJiObj);
      }
    }
  }
  
  private void SWdatadeal(String linkName, CaiJi CaiJiObj)
  {
    ArrayList<JSONObject> JAs = Swritedatas.getSWriteArrayListBylinkName(linkName, true);
    if ((JAs.size() > 0) && (CaiJiObj.isCaijiBZ()))
    {
      System.out.println("??????");
      CaijiRunable_T CaijiRunable_T = (CaijiRunable_T)this.CaijiRunable_Ts.get(linkName);
      boolean rs = CaijiRunable_T.IniWCaijiRunable_T(linkName, Integer.valueOf(0), CaiJiObj.getDeviceName(), Integer.valueOf(CaiJiObj.getDeviceAddr()), "WS", JAs);
      if (rs) {
        this.Threadpool.execute(CaijiRunable_T);
      }
    }
  }
  
  private void RWovertimedeal(Timing Timingobj, RWSign RWSignObj, CaiJi CaiJiObj)
  {
    int exceptnum = 10;
    if (!RWSignObj.isAcquisitionCompleted()) {
      if (Timingobj.getCollectionLongestWaitingTime() < CaiJiObj.getOvertime() * 10 / exceptnum)
      {
        Timingobj.addCollectionLongestWaitingTime();
      }
      else
      {
        RWSignObj.setAcquisitionCompleted(true);
        Timingobj.ResetCollectionLongestWaitingTime();
      }
    }
    if (!RWSignObj.isInternalWriteCompletion()) {
      if (Timingobj.getInternalWriteLongestWaitingTime() < CaiJiObj.getOvertime() * 10 / exceptnum)
      {
        Timingobj.addInternalWriteLongestWaitingTime();
      }
      else
      {
        RWSignObj.setInternalWriteCompletion(true);
        Timingobj.ResetInternalWriteLongestWaitingTime();
        Timingobj.ResetInternalWriteInterval();
      }
    }
    if (!RWSignObj.isManualWriteCompletion()) {
      if (Timingobj.getSdWriteLongestWaitingTime() < CaiJiObj.getOvertime() * 10 / exceptnum)
      {
        Timingobj.addSdWriteLongestWaitingTime();
      }
      else
      {
        RWSignObj.setManualWriteCompletion(true);
        Timingobj.ResetSdWriteLongestWaitingTime();
      }
    }
  }
  
  private void RWtimedeal(String linkName, Integer i, Timing Timingobj, RWSign RWSignObj, CaiJi CaiJiObj)
  {
    int exceptnum = 10;
    if (CaiJiObj.isCaijiBZ())
    {
      if (Timingobj.getCollectionInterval() - 10 * i.intValue() < CaiJiObj.getCJtime() / exceptnum)
      {
        Timingobj.addCollectionInterval();
      }
      else if (RWSignObj.isAcquisitionCompleted())
      {
        DabaoResultdate DabaoResultdateObj = CaiJiObj.getDabaoResultdateObj();
        if (DabaoResultdateObj != null)
        {
          CaijiRunable_T CaijiRunable_T = (CaijiRunable_T)this.CaijiRunable_Ts.get(linkName);
          boolean rs = CaijiRunable_T.IniRCaijiRunable_T(linkName, i, CaiJiObj.getDeviceName(), Integer.valueOf(CaiJiObj.getDeviceAddr()), DabaoResultdateObj);
          if (rs)
          {
            RWSignObj.setAcquisitionCompleted(false);
            this.Threadpool.execute(CaijiRunable_T);
            try
            {
              Thread.sleep(2L);
            }
            catch (InterruptedException e)
            {
              e.printStackTrace();
            }
            if (!CaijiRunable_T.isISMatch()) {
              RWSignObj.setAcquisitionCompleted(true);
            }
          }
        }
        else
        {
          Timingobj.ResetCollectionInterval();
          JSONArray jArray = (JSONArray)this.DecJSONArrays.get(CaiJiObj.getDeviceName());
          if (jArray.length() == CaiJiObj.getRegNum()) {
            this.ModCallBackObj.DecDataComing(CaiJiObj.getDeviceName(), Boolean.valueOf(true), jArray);
          }
          jArray = new JSONArray();
          this.DecJSONArrays.put(CaiJiObj.getDeviceName(), jArray);
        }
      }
      if (Timingobj.getInternalWriteInterval() - 10 * i.intValue() < XTconf.getWriteDatafrequency() / exceptnum)
      {
        Timingobj.addInternalWriteInterval();
      }
      else if (RWSignObj.isInternalWriteCompletion())
      {
        ArrayList<JSONObject> WriteArrayList = Zwritedatas.getWriteArrayListBylinkNameDecname(linkName, CaiJiObj.getDeviceName());
        if (WriteArrayList.size() > 0)
        {
          CaijiRunable_T CaijiRunable_T = (CaijiRunable_T)this.CaijiRunable_Ts.get(linkName);
          boolean rs = CaijiRunable_T.IniWCaijiRunable_T(linkName, i, CaiJiObj.getDeviceName(), Integer.valueOf(CaiJiObj.getDeviceAddr()), "WZ", WriteArrayList);
          if (rs)
          {
            RWSignObj.setInternalWriteCompletion(false);
            this.Threadpool.execute(CaijiRunable_T);
            try
            {
              Thread.sleep(2L);
            }
            catch (InterruptedException e)
            {
              e.printStackTrace();
            }
            if (!CaijiRunable_T.isISMatch())
            {
              Timingobj.ResetInternalWriteInterval();
              RWSignObj.setInternalWriteCompletion(true);
            }
          }
        }
      }
    }
    else if (Timingobj.getReconnectTime() < CaiJiObj.getCLtime() / exceptnum)
    {
      Timingobj.addReconnectTime();
    }
    else
    {
      Timingobj.ResetReconnectTime();
      Timingobj.setCollectionInterval(CaiJiObj.getCJtime() / exceptnum);
      Timingobj.setInternalWriteInterval(CaiJiObj.getCJtime() / exceptnum);
      RWSignObj.setAcquisitionCompleted(true);
      Timingobj.ResetCollectionLongestWaitingTime();
      RWSignObj.setInternalWriteCompletion(true);
      Timingobj.ResetInternalWriteLongestWaitingTime();
      RWSignObj.setManualWriteCompletion(true);
      Timingobj.ResetSdWriteLongestWaitingTime();
      CaiJiObj.setCaijiBZ(true);
    }
  }
  
  public void IniDeviceCaiji(Map<String, ArrayList<CaiJi>> deviceCaiji, Map<String, ModbusTransaction> master)
  {
    if (deviceCaiji != null)
    {
      this.deviceCaijis = deviceCaiji;
      Set<String> LinkNames = this.deviceCaijis.keySet();
      this.Linknum = LinkNames.size();
      if ((this.Linknum > 0) && (this.BaoCallBackObj != null) && (master != null))
      {
        System.out.println("Linknum:" + this.Linknum);
        this.Threadpool = Executors.newFixedThreadPool(this.Linknum * 3);
        for (String LinkName : LinkNames) {
          if (master.get(LinkName) != null)
          {
            CaijiRunable_T LinkDecCaijiRunable_T = CaijiRunable_T.getCaijiRunable_T(this.BaoCallBackObj, (ModbusTransaction)master.get(LinkName));
            int LinkDecnum = ((ArrayList)this.deviceCaijis.get(LinkName)).size();
            if ((LinkDecCaijiRunable_T != null) && (LinkDecnum > 0))
            {
              this.CaijiRunable_Ts.put(LinkName, LinkDecCaijiRunable_T);
              this.Link_Decnum.put(LinkName, Integer.valueOf(LinkDecnum));
              ArrayList<Timing> LinkDecTiming = IniLinkDecTiming(LinkDecnum);
              ArrayList<RWSign> LinkDecRWSign = IniLinkDecRWSign(LinkDecnum);
              this.Timings.put(LinkName, LinkDecTiming);
              this.RWSigns.put(LinkName, LinkDecRWSign);
              for (int i = 0; i < LinkDecnum; i++)
              {
                JSONArray jArray = new JSONArray();
                this.DecJSONArrays.put(((CaiJi)((ArrayList)this.deviceCaijis.get(LinkName)).get(i)).getDeviceName(), jArray);
                if (!this.isclose) {
                  this.isclose = true;
                }
              }
            }
          }
        }
      }
    }
  }
  
  private ArrayList<Timing> IniLinkDecTiming(int LinkDecnum)
  {
    ArrayList<Timing> LinkDecTiming = new ArrayList();
    while (LinkDecnum > 0)
    {
      LinkDecnum--;
      Timing TimingObj = new Timing();
      LinkDecTiming.add(TimingObj);
    }
    return LinkDecTiming;
  }
  
  private ArrayList<RWSign> IniLinkDecRWSign(int LinkDecnum)
  {
    ArrayList<RWSign> LinkDecRWSign = new ArrayList();
    while (LinkDecnum > 0)
    {
      LinkDecnum--;
      RWSign RWSignsObj = new RWSign();
      LinkDecRWSign.add(RWSignsObj);
    }
    return LinkDecRWSign;
  }
  
  private class BaoCallBack
    implements RWBaoCallBack
  {
    private BaoCallBack() {}
    
    public void ReadCompleted(String linkname, int index, String Devicename, Boolean iss, JSONArray JSAobj)
    {
      ((RWSign)((ArrayList)ManagementThread_T.this.RWSigns.get(linkname)).get(index)).setAcquisitionCompleted(true);
      ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetCollectionLongestWaitingTime();
      if (iss.booleanValue())
      {
        ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetCollectionBad();
        JSONArray jArray = (JSONArray)ManagementThread_T.this.DecJSONArrays.get(Devicename);
        for (int i = 0; i < JSAobj.length(); i++) {
          jArray.put(JSAobj.get(i));
        }
        ManagementThread_T.this.DecJSONArrays.put(Devicename, jArray);
      }
      else
      {
        flieIO.LOG(XTconf.geterrLogerroDizhi(), Devicename + "????????");
        ManagementThread_T.this.DecJSONArrays.put(Devicename, new JSONArray());
        ((CaiJi)((ArrayList)ManagementThread_T.this.deviceCaijis.get(linkname)).get(index)).ResetCauIndex();
        ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetCollectionInterval();
        ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).addCollectionBad();
        if (((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).getCollectionBad() > 5)
        {
          ManagementThread_T.this.ModCallBackObj.DecDataComing(Devicename, iss, new JSONArray());
          ((CaiJi)((ArrayList)ManagementThread_T.this.deviceCaijis.get(linkname)).get(index)).setCaijiBZ(false);
          ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetCollectionBad();
          ManagementThread_T.this.ModCallBackObj.Communicationfailure(Devicename, Integer.valueOf(((CaiJi)((ArrayList)ManagementThread_T.this.deviceCaijis.get(linkname)).get(index)).getDeviceAddr()));
        }
      }
    }
    
    public void SDWriteCompleted(String linkname, int index, Boolean WRS, writeobj writeobj)
    {
      ((RWSign)((ArrayList)ManagementThread_T.this.RWSigns.get(linkname)).get(index)).setManualWriteCompletion(true);
      ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetSdWriteLongestWaitingTime();
      ManagementThread_T.this.ModCallBackObj.SDWriteDataEnd(WRS, writeobj);
    }
    
    public void ZDWriteCompleted(String linkname, int index)
    {
      ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetInternalWriteLongestWaitingTime();
      ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetInternalWriteInterval();
      ((RWSign)((ArrayList)ManagementThread_T.this.RWSigns.get(linkname)).get(index)).setInternalWriteCompletion(true);
    }
    
    public void ZDWriteOnce(Boolean writeRS, writeobj writeobj)
    {
      ManagementThread_T.this.ModCallBackObj.ZDWriteDataEnd(writeRS, writeobj);
    }
    
    public void errCallBack(String R_w, String linkname, int index)
    {
      if (R_w.equals("R"))
      {
        ((RWSign)((ArrayList)ManagementThread_T.this.RWSigns.get(linkname)).get(index)).setAcquisitionCompleted(true);
        ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetCollectionLongestWaitingTime();
      }
      if (R_w.equals("WZ"))
      {
        ((RWSign)((ArrayList)ManagementThread_T.this.RWSigns.get(linkname)).get(index)).setInternalWriteCompletion(true);
        ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetInternalWriteLongestWaitingTime();
        ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetInternalWriteInterval();
      }
      if (R_w.equals("WS"))
      {
        ((RWSign)((ArrayList)ManagementThread_T.this.RWSigns.get(linkname)).get(index)).setManualWriteCompletion(true);
        ((Timing)((ArrayList)ManagementThread_T.this.Timings.get(linkname)).get(index)).ResetSdWriteLongestWaitingTime();
      }
      System.out.println("err0:" + R_w + "," + linkname + "," + index);
    }
  }
}
