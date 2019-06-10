/**
*  Presence Governor - Child
*
*	Author: Doug Beard
*
*  Original Copyright below, this work has been heavily modified with really only the original concept remaining.
* 
*  Copyright 2019 Doug Beard
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
* 
*  Raw: https://raw.githubusercontent.com/omayhemo/Hubitat-Combined-Presence/master/combinedPresenceInstance.groovy
*
*  See Release Notes at Bottom
*/
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper

definition(
    name: "Presence Governor - Child",
    namespace: "dwb",
	parent:	"dwb:Presence Governor",
    author: "Doug Beard",
    description: "Combine the states of multiple presence sensors into a single proxy state",
    category: "Safety & Security",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


def inputGeofence = [
		name:				"inputSensors",
		type:				"capability.presenceSensor",
		title:				"Presence Sensors",
		description: 		"Usually these are the Geolocation apps you have on your phone that change a virtual presence device's state",
		multiple:			true,
		required:			false
	]

def wifiPresenceSensor = [
		name:				"wifiSensor",
		type:				"capability.presenceSensor",
		title:				"WiFi Sensor",
		multiple:			false,
		required:			false
	]

def fobPresenceSensor = [
		name:				"fobSensor",
		type:				"capability.presenceSensor",
		title:				"Fob Sensor",
		multiple:			false,
		required:			false
	]

def outputSensor = [
		name:				"outputSensor",
		type:				"capability.presenceSensor",
		title:				"Proxy",
		multiple:			false,
		required:			true
	]

def thresholdInput = [
		name:				"threshold",
		type:				"int",
		title:				"Departure Threshold",
		multiple:			false,
		required:			true
	]

def thresholdArrivalInput = [
		name:				"arrivalthreshold",
		type:				"int",
		title:				"Arrival Threshold",
		multiple:			false,
		required:			true
	]

def departureDelay = [
		name:				"departuredelay",
		type:				"int",
		title:				"Departure Delay (in minutes)",
		default: 0,
		multiple:			false,
		required:			true
	]

def lockInput = [
		name: 				"lockCodeLock",
		type:				"capability.lock",
		title:				"Which lock to use for Lock codes", 
		required:			false,
		multiple:			false,
		submitOnChange:		true
	
	]

preferences {
	page(name: "mainPage", title: "<b>Presence Sensors:</b>", install: true, uninstall: true) {
		section("") {
			input inputGeofence

			if (inputSensors){
				input thresholdInput
				input thresholdArrivalInput
			}
			
			input outputSensor
			input wifiPresenceSensor
			input fobPresenceSensor
			input lockInput
			
			def lcText = lockCodeLock?.currentValue("lockCodes")
			if (!lcText?.startsWith("{")) {
				lcText = decrypt(lcText)
			}
			def lockCodesRaw
			def lockCodes = []
			if (lcText) lockCodesRaw = new JsonSlurper().parseText(lcText)
			//ifDebug("lockCodes for selected lock: ${lockCodesRaw}")
			lockCodesRaw.each{
				//ifDebug("lockCode ${it}")
				def lockCodeValue = it.getValue()
				ifDebug("lockCodeValue.name: ${lockCodeValue.name} - lockCodeValue.code: ${lockCodeValue.code}")
				lockCodes << ["${lockCodeValue.code}": "${lockCodeValue.name}"]
			}

			input "selectedLockCodes", 'enum', title: "Lock Code will trigger present", required: false, multiple:true, submitOnChange:true, options: lockCodes
			
			input departureDelay
			
		}
		
		
		
		section("") {
            input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
        }
	}
}

def installed() {
	log.info "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.info "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	if (inputSensors){
		subscribe(inputSensors, "presence.present", presenceChangedHandler)
		subscribe(inputSensors, "presence.not present", presenceChangedHandler)	
	}
	if (wifiSensor){
		subscribe(wifiSensor, "presence.not present", wifiPresenceChangedHandler)
		subscribe(wifiSensor, "presence.present", wifiPresenceChangedHandler)
	}
	if (fobSensor){
		subscribe(fobSensor, "presence.not present", fobPresenceChangedHandler)
		subscribe(fobSensor, "presence.present", fobPresenceChangedHandler)
	}
	if (lockCodeLock){
		subscribe(lockCodeLock,"lock",lockUseHandler)	
	}
	app.updateLabel("Presence Governor for ${outputSensor.displayName}")
}

def wifiPresenceChangedHandler(evt) {
	sendEvent(name:"Presence Changed", value: "$evt.device - $evt.value", displayed:false, isStateChange: false)
	switch(evt.value){
		case "not present":
			//if(departureCheck()){
				//departed()	
			//}
			ifDebug("WiFi Presence, not present not considered")
			break
		case "present":
			ifDebug("WiFi Presence, skip threshold check, mark all other device arrived")
			/* 	WiFi sensor present is the ultimate truth 			*/
			inputSensors.each{
			 	if (!hasNotificationCapability(it.capabilities)){
					it.arrived()
				} else {
					ifDebug("I think I found the HE Mobile App, skipping forced arrival on ${it.name}")
				}
			}

			arrived()
	}
}

def hasNotificationCapability(capabilities){
	ifDebug("hasNotificationCapability")
	def found = capabilities.find{
		if (it.toString() == "Notification") {
			ifDebug("Notification Capability Found")
			return true
		}
		return false;
	}
	return found
}

def fobPresenceChangedHandler(evt) {
	sendEvent(name:"Presence Changed", value: "$evt.device - $evt.value", displayed:false, isStateChange: false)
	switch(evt.value){
		case "not present":
			ifDebug("Fob Present, skip threshold check")
			departed()
			break
		case "present":
			ifDebug("Fob Present, skip threshold check")
			arrived()
			break
	}
}

def presenceChangedHandler(evt) {
	sendEvent(name:"Presence Changed", value: "$evt.device - $evt.value", displayed:false, isStateChange: false)
	switch(evt.value){
		case "not present":
			if (departureCheck()){
				departed()	
			}
			break
		case "present":
			if (arrivalCheck()){
				arrived()	
			}
			break
	}
}

def lockUseHandler(evt){
	log.warn "lockUseHandler ${evt.displayName}"
    def data = evt.data
    def isEncrypted = false
    if (data && !data[0].startsWith("{")) {
       ifDebug("encr data:${data}")
       data = decrypt(data)
       isEncrypted = true
    }
	
	if (data){
		ifDebug("lockUseHandler- device:${evt.displayName}, value:${evt.value}, data:${data}, type:${evt.type}, wasEncrypted:${isEncrypted}")
		def dataJson = new JsonSlurper().parseText(data)
		ifDebug("dataJson - ${dataJson}")
		dataJson.each{
			ifDebug(it)
			def lockCodeValue = it.getValue()
			ifDebug(lockCodeValue)
			ifDebug(lockCodeValue.code)
			def foundCode = selectedLockCodes.find{it == lockCodeValue.code}
			if (foundCode){
				ifDebug("Found Lock Code")
				arrived()
			}	
    	}
	}
    
}

def departureCheck(){
	ifDebug("Departure Check")
	if (fobSensor && fobSensor.currentValue("presence") == "not present"){
		ifDebug("Fob not present override")
		return true
	}else{
		if (checkDepartedThreshold()){
			return true
		}
	}	
	return false
}

def checkDepartedThreshold(){
	int count = 0
	inputSensors.each { inputSensor ->
	if (inputSensor.currentValue("presence") == "not present") {
		ifDebug("${inputSensor.label} not present")
			count++
		}
	}
	ifDebug("$count sensors not present")
	if (count >= Integer.parseInt(threshold)){
		ifDebug("Threshold met setting not present")
		sendEvent(name:"Threshold", value: "$threshold met", displayed:false, isStateChange: false)
		return true
	}		
	ifDebug("Threshold not met")
	return false
}

def arrivalCheck(){
	ifDebug("Arrival Check")
	return checkArrivalThreshold()
}

def checkArrivalThreshold(){
	int count = 0
	inputSensors.each { inputSensor ->
		if (inputSensor.currentValue("presence") == "present") {
			sendEvent(name:"Arrival Threshold", value: "$arrivalthreshold met", displayed:false, isStateChange: false)
			ifDebug("${inputSensor.label} present")
			count++
		}
	}
	ifDebug("$count sensors present")
	if (count >= Integer.parseInt(arrivalthreshold)){
		ifDebug("Threshold met setting present")
		return true
	}	
	ifDebug("Threshold not met")
	return false
}

def departed(){
	if (departuredelay > 0){
		int delaySec = departuredelay as Integer
		int thresholdMin = delaySec * 60
		ifDebug("Waiting ${thresholdMin} seconds to depart")
		runIn(thresholdMin, delayedDeparture)
	} else {
		delayedDeparture()	
	}
}

def delayedDeparture(){
	ifDebug("Departing")
	outputSensor.departed()
	sendEvent(name:"Presence Governor", value: "departed", displayed:false, isStateChange: false)
}

def arrived(){
	ifDebug("Arriving")
	unschedule()
	sendEvent(name:"Presence Governor", value: "arrived", displayed:false, isStateChange: false)
	outputSensor.arrived()	
}

private ifDebug(msg)     
{  
    if (msg && isDebug)  log.debug "Presence Governor for $outputSensor.displayName: " + msg  
}

/**
*	Version 1.2.2
*		New routine for WiFi handler setting inputSensors to arrived
* 	Version 1.2.1
* 		Fix Fob Null Reference
*		Fix illadviced cleverness on WiFi routine and inputSensors.arrived()
*	Version 1.2
*		WiFi ulimate source of present truth, but ignored for departure
*			When arrival events are missed, each output device get's caught in limbo.
*			If WiFi is present set them back in sync in prep for the next event.
*  	Version 1.1
*		Refactored for DRY
*		Fob not present now overrides departure threshold check
*/

/**	Inspired and Adapted from Joel Wetzel's excellent offering. https://github.com/joelwetzel/Hubitat-Combined-Presence **/







