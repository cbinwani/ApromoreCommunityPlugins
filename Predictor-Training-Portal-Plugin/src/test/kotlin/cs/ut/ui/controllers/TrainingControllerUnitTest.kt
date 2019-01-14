package cs.ut.ui.controllers

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringReader
import java.util.Arrays
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.deckfour.xes.`in`.XesXmlParser
import org.deckfour.xes.model.XLog
import org.json.JSONObject
import org.junit.Ignore
import org.junit.Test

class TrainingControllerUnitTest {

  @Test
  fun testConvertXLogToDatasetParams_bpi12(): Unit {
      val inputStream = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("test_bpi12.xes")
      val log = XesXmlParser().parse(inputStream).`get`(0)
      val result = TrainingController.convertXLogToDatasetParams(log)

      val inputStream2 = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("test_bpi12.json")
      val expected = JSONObject(String(inputStream2.readBytes()))

      assertEquals(expected.toString(), result.toString())

      //assertEquals(expected.get("case_id_col"), result.get("case_id_col"))
      //assertEquals(expected.get("dynamic_cat_cols"), result.get("dynamic_cat_cols"))
  }

  @Test
    fun testConvertXLogToDatasetParams_sepsis(): Unit {
        val inputStream = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("sepsis.xes")
        val log = XesXmlParser().parse(inputStream).`get`(0)
        val result = TrainingController.convertXLogToDatasetParams(log)

        val inputStream2 = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("sepsis.json")
        val expected = JSONObject(String(inputStream2.readBytes()))

        assertEquals(expected.toString(), result.toString())

  }

  @Test
  fun testConvertXLogToDatasetParams_missingHeader(): Unit {
      val inputStream = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("missing_header.xes")
      val log = XesXmlParser().parse(inputStream).`get`(0)
      val result = TrainingController.convertXLogToDatasetParams(log)

      val inputStream2 = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("missing_header.json")
      val expected = JSONObject(String(inputStream2.readBytes()))

      assertEquals(expected.toString(), result.toString())
  }

  @Test
  fun testConvertXLogToCSV_sepsis() : Unit {
      val inputStream = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("sepsis.xes")
      val log = XesXmlParser().parse(inputStream).`get`(0)

      val outputStream = ByteArrayOutputStream()
      TrainingController.convertXLogToCSV(log, outputStream)
      val reader = BufferedReader(StringReader(outputStream.toString()))
      assertEquals("case_id,InfectionSuspected,org:group,DiagnosticBlood,DisfuncOrg,SIRSCritTachypnea,Hypotensie,SIRSCritHeartRate,Infusion,DiagnosticArtAstrup,concept:name,Age,DiagnosticIC,DiagnosticSputum,DiagnosticLiquor,DiagnosticOther,SIRSCriteria2OrMore,DiagnosticXthorax,SIRSCritTemperature,time:timestamp,DiagnosticUrinaryCulture,SIRSCritLeucos,Oligurie,DiagnosticLacticAcid,lifecycle:transition,Diagnose,Hypoxie,DiagnosticUrinarySediment,DiagnosticECG,Leucocytes,CRP,LacticAcid", reader.readLine())
      assertEquals("A,true,A,true,true,true,true,true,true,true,ER Registration,85,true,false,false,false,true,true,true,2014-10-22T09:15:41,true,false,false,true,complete,A,false,true,true,,", reader.readLine())
      assertEquals("A,,B,,,,,,,,Leucocytes,,,,,,,,,2014-10-22T09:27:00,,,,,complete,,,,,9.6,", reader.readLine())
  }

  @Test
  fun testConvertXLogToCSV_missingHeader() : Unit {
      val inputStream = TrainingControllerUnitTest::class.java.getClassLoader().getResourceAsStream("missing_header.xes")
      val log = XesXmlParser().parse(inputStream).`get`(0)

      val outputStream = ByteArrayOutputStream()
      TrainingController.convertXLogToCSV(log, outputStream)
      val reader = BufferedReader(StringReader(outputStream.toString()))
      assertEquals("case_id,LoanGoal,Action,RequestedAmount,Resource,activity_duration,weekday,FirstWithdrawalAmount,time:timestamp,NumberOfTerms,duration,elapsed,ApplicationType,remtime,concept:name,MonthlyCost,month,hour,EventOrigin,lifecycle:transition,CreditScore,OfferedAmount", reader.readLine())
      assertEquals("c_617529015,Home improvement,Created,20000.0,User_1,0.0,4,0.0,2016-09-09T06:58:02,0.0,0.0,0.0,New credit,2761324.019,A_Create Application,0.0,9,16,Application,complete,missing", reader.readLine())
      assertEquals("c_617529015,Home improvement,statechange,20000.0,User_1,0.0,4,0.0,2016-09-09T06:58:02,0.0,0.00106666666667,0.064,New credit,2761323.955,A_Submitted,0.0,9,16,Application,complete,missing", reader.readLine())
      assertNull(reader.readLine())
  }

  @Test
  fun testWriteCSV() : Unit {
       val values = Arrays.asList("one", "two,three", "\"four\"", "five\nsix")
       val outputStream = ByteArrayOutputStream()
       val printWriter = PrintWriter(outputStream)
       TrainingController.writeCSV(values, printWriter)
       printWriter.close()

       assertEquals("one,\"two,three\",\"\"\"four\"\"\",\"five\nsix\"\n", outputStream.toString())
  }
}
