package com.pandafolks.mattszm.panda

import com.github.pandafolks.panda.utils.PandaStartupException
import com.pandafolks.mattszm.panda.sequence.Sequence.SEQUENCE_COLLECTION_NAME
import monix.connect.mongodb.client.CollectionCodecRef
import org.slf4j.LoggerFactory

package object sequence {
  private val logger = LoggerFactory.getLogger(getClass.getName)

  private var sequenceDao: Option[SequenceDao] = Option.empty
  private var sequenceCol: Option[CollectionCodecRef[Sequence]] = Option.empty

  def launch(dbName: String)(sequenceColName: String = SEQUENCE_COLLECTION_NAME): Unit = {
    logger.info("Creating \'sequence\' module...")

    sequenceDao = Some(new SequenceDao())
    sequenceCol = Some(Sequence.getCollection(dbName, sequenceColName))

    logger.info("\'sequence\' module created successfully")
  }

  def getSequenceDao: SequenceDao =
    try {
      sequenceDao.get
    } catch {
      case _: NoSuchElementException =>
        logger.error("SequenceDao not initialized - launch the \'sequence\' module firstly")
        throw new PandaStartupException("\'sequence\' module is not initialized properly")
    }

  def getSequenceCol: CollectionCodecRef[Sequence] =
    try {
      sequenceCol.get
    } catch {
      case _: NoSuchElementException =>
        logger.error("sequenceCol [CollectionCodecRef[Sequence]] not initialized - launch the \'sequence\' module firstly")
        throw new PandaStartupException("\'sequence\' module is not initialized properly")
    }
}
