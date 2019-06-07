/**
*  Presence Governor
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

*
*   https://raw.githubusercontent.com/omayhemo/Hubitat-Combined-Presence/master/combinedPresence.groovy
*/


definition(
    name: "Presence Governor",
    namespace: "dwb",
    author: "Doug Beard",
    description: "Combine the states of multiple presence sensors into a single proxy state.",
    category: "Safety & Security",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")


preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
}


def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}


def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}


def initialize() {
    log.info "There are ${childApps.size()} child apps"
    childApps.each { child ->
    	log.info "Child app: ${child.label}"
    }
}


def installCheck() {         
	state.appInstalled = app.getInstallationState()
	
	if (state.appInstalled != 'COMPLETE') {
		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	}
  	else {
    	log.info "Parent Installed"
  	}
}

def mainPage() {
    dynamicPage(name: "mainPage") {
    	installCheck()
		
		if (state.appInstalled == 'COMPLETE') {
			section("<H1>${app.label}</H1>") {
				paragraph "Combine multiple presence devices with various logical checks to determine if present.  Geofencing with a Threshold.  Fobs, WiFi Presence driver, Lock Codes"
			}
  			section("") {
				app(name: "anyOpenApp", appName: "Presence Governor - Child", namespace: "dwb", title: "<b>Add/Edit Governors</b>", multiple: true)
			}
			section("") {
       			label title: "Enter a name for parent app (optional)", required: false
 			}
		}
	}
}

/**
*	Version 1.0
*	All New Presence Governor
*/

/**	Inspired and Adapted from Joel Wetzel's excellent offering. https://github.com/joelwetzel/Hubitat-Combined-Presence **/

