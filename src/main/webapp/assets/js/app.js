(function () {
  var menu = document.querySelector('.menu-toggle');
  if (menu) {
    menu.addEventListener('click', function () {
      var open = menu.getAttribute('aria-expanded') === 'true';
      menu.setAttribute('aria-expanded', String(!open));
      var nav = document.querySelector('.main-nav');
      if (nav) nav.classList.toggle('nav-open', !open);
    });
  }

  var printButtons = document.querySelectorAll('.print-trigger');
  for (var i = 0; i < printButtons.length; i++) {
    printButtons[i].addEventListener('click', function () { window.print(); });
  }

  var confirmForms = document.querySelectorAll('form[data-confirm]');
  for (var j = 0; j < confirmForms.length; j++) {
    confirmForms[j].addEventListener('submit', function (event) {
      if (!window.confirm(this.getAttribute('data-confirm'))) event.preventDefault();
    });
  }

  var checkoutForm = document.querySelector('.checkout-form');
  if (checkoutForm) {
    var savedAddressSelect = checkoutForm.querySelector('#savedAddressSelect');
    var manualAddressPanel = checkoutForm.querySelector('#manualCheckoutAddress');
    var stateField = checkoutForm.querySelector('#checkoutState');
    var pinField = checkoutForm.querySelector('#checkoutPin');
    var districtField = checkoutForm.querySelector('#checkoutDistrict');
    var cityField = checkoutForm.querySelector('#checkoutCity');
    var pinStatus = checkoutForm.querySelector('#pinLookupStatus');
    var manualToggle = checkoutForm.querySelector('#manualLocationToggle');
    var manualFields = checkoutForm.querySelector('#manualLocationFields');
    var manualDistrict = checkoutForm.elements.districtFallback;
    var manualCity = checkoutForm.elements.cityFallback;
    var offices = [];
    var lookupSucceeded = false;
    var requestNumber = 0;

    function syncSavedAddress() {
      if (!savedAddressSelect || !manualAddressPanel) return;
      var usingSavedAddress = Boolean(savedAddressSelect.value);
      manualAddressPanel.hidden = usingSavedAddress;
      Array.prototype.forEach.call(manualAddressPanel.querySelectorAll('input,select,button'), function (field) {
        field.disabled = usingSavedAddress;
        if (usingSavedAddress) field.required = false;
      });
      if (!usingSavedAddress) {
        manualAddressPanel.querySelector('[name="line1"]').required = true;
        stateField.required = true;
        pinField.required = true;
        manualFields.hidden = true;
        manualDistrict.disabled = true;
        manualCity.disabled = true;
        manualDistrict.required = false;
        manualCity.required = false;
        resetLocationResults();
      }
    }

    function addOption(select, value, label) {
      var option = document.createElement('option');
      option.value = value;
      option.textContent = label;
      select.append(option);
    }

    function resetLocationResults() {
      lookupSucceeded = false;
      offices = [];
      districtField.replaceChildren();
      cityField.replaceChildren();
      addOption(districtField, '', 'Enter PIN code first');
      addOption(cityField, '', 'Enter PIN code first');
      districtField.disabled = true;
      cityField.disabled = true;
      districtField.required = false;
      cityField.required = false;
    }

    function useManualLocation(message) {
      resetLocationResults();
      manualFields.hidden = false;
      manualDistrict.disabled = false;
      manualCity.disabled = false;
      manualDistrict.required = true;
      manualCity.required = true;
      manualToggle.textContent = 'Use PIN code results instead';
      pinStatus.textContent = message;
    }

    function usePinLocation() {
      manualFields.hidden = true;
      manualDistrict.disabled = true;
      manualCity.disabled = true;
      manualDistrict.required = false;
      manualCity.required = false;
      manualToggle.textContent = 'Can’t load PIN results? Enter city and district manually';
    }

    function populateCities() {
      cityField.replaceChildren();
      addOption(cityField, '', 'Choose a city / post office');
      var selectedDistrict = districtField.value;
      var names = {};
      offices.forEach(function (office) {
        if (office.District !== selectedDistrict || names[office.Name]) return;
        names[office.Name] = true;
        addOption(cityField, office.Name, office.Name + (office.BranchType ? ' · ' + office.BranchType : ''));
      });
      cityField.disabled = !selectedDistrict;
      cityField.required = Boolean(selectedDistrict);
    }

    function stateNameMatches(apiState, selectedState) {
      function normalize(value) { return String(value || '').toLowerCase().replace(/[^a-z]/g, ''); }
      var aliases = {
        'odisha': ['odisha', 'orissa'],
        'puducherry': ['puducherry', 'pondicherry'],
        'delhi': ['delhi', 'nctofdelhi', 'nationalcapitalterritoryofdelhi'],
        'jammuandkashmir': ['jammuandkashmir', 'jammuandkashmirunionterritory']
      };
      var selected = normalize(selectedState);
      var actual = normalize(apiState);
      return selected === actual || (aliases[selected] || []).indexOf(actual) >= 0;
    }

    function lookupPin() {
      var pin = pinField.value.trim();
      if (!/^[0-9]{6}$/.test(pin)) {
        requestNumber++;
        resetLocationResults();
        pinStatus.textContent = 'Enter a six-digit PIN code to load its districts and post offices.';
        return;
      }
      if (!stateField.value) {
        resetLocationResults();
        pinStatus.textContent = 'Choose your state first, then enter the PIN code.';
        return;
      }
      var currentRequest = ++requestNumber;
      usePinLocation();
      pinStatus.textContent = 'Looking up this PIN code…';
      window.fetch(checkoutForm.getAttribute('data-pin-lookup') + '?pin=' + encodeURIComponent(pin), { headers: { Accept: 'application/json' } })
        .then(function (response) {
          if (!response.ok) throw new Error('PIN lookup is temporarily unavailable.');
          return response.json();
        })
        .then(function (result) {
          if (currentRequest !== requestNumber) return;
          var record = Array.isArray(result) ? result[0] : null;
          var returnedOffices = record && record.Status === 'Success' && Array.isArray(record.PostOffice) ? record.PostOffice : [];
          var matching = returnedOffices.filter(function (office) { return stateNameMatches(office.State, stateField.value); });
          if (!matching.length) {
            useManualLocation(returnedOffices.length ? 'That PIN code belongs to a different state. Check the state, or enter your city and district manually.' : 'No post offices were found for that PIN code. Check it, or enter your city and district manually.');
            return;
          }
          offices = matching;
          var districts = {};
          matching.forEach(function (office) { if (office.District) districts[office.District] = true; });
          var districtNames = Object.keys(districts).sort();
          districtField.replaceChildren();
          addOption(districtField, '', 'Choose your district');
          districtNames.forEach(function (district) { addOption(districtField, district, district); });
          districtField.disabled = false;
          districtField.required = true;
          districtField.onchange = populateCities;
          cityField.replaceChildren();
          addOption(cityField, '', 'Choose a city / post office');
          cityField.disabled = true;
          cityField.required = false;
          lookupSucceeded = true;
          pinStatus.textContent = 'PIN code matched. Choose the district and delivery post office.';
          if (districtNames.length === 1) {
            districtField.value = districtNames[0];
            populateCities();
          }
        })
        .catch(function () {
          if (currentRequest !== requestNumber) return;
          useManualLocation('PIN lookup is unavailable right now. Enter your city and district manually to continue.');
        });
    }

    pinField.addEventListener('input', function () {
      resetLocationResults();
      if (pinField.value.length === 6) lookupPin();
    });
    pinField.addEventListener('change', lookupPin);
    stateField.addEventListener('change', function () { if (pinField.value.length === 6) lookupPin(); });
    manualToggle.addEventListener('click', function () {
      if (manualFields.hidden) useManualLocation('Enter the city and district that match the address and PIN code.');
      else if (lookupSucceeded) {
        usePinLocation();
        manualFields.hidden = true;
        manualDistrict.value = '';
        manualCity.value = '';
      } else lookupPin();
    });
    if (savedAddressSelect) {
      savedAddressSelect.addEventListener('change', syncSavedAddress);
      syncSavedAddress();
    }
    checkoutForm.addEventListener('submit', function (event) {
      if (savedAddressSelect && savedAddressSelect.value) return;
      if (manualFields.hidden && !lookupSucceeded) {
        event.preventDefault();
        pinStatus.textContent = 'Wait for the PIN lookup, or choose manual city and district entry.';
      }
    });
  }

  (function initializeVideoCall() {
    var panel = document.querySelector('[data-video-call]');
    if (!panel) return;
    var endpoint = panel.getAttribute('data-endpoint');
    var appointment = panel.getAttribute('data-appointment');
    var csrf = panel.getAttribute('data-csrf');
    var userId = Number(panel.getAttribute('data-user-id'));
    var userRole = panel.getAttribute('data-user-role');
    var patientAppointmentsUrl = panel.getAttribute('data-patient-appointments');
    var state = panel.querySelector('[data-video-state]');
    var error = panel.querySelector('[data-video-error]');
    var startButton = panel.querySelector('[data-video-start]');
    var acceptButton = panel.querySelector('[data-video-accept]');
    var endButton = panel.querySelector('[data-video-end]');
    var micButton = panel.querySelector('[data-video-mic]');
    var cameraButton = panel.querySelector('[data-video-camera]');
    var localVideo = panel.querySelector('[data-video-local]');
    var remoteVideo = panel.querySelector('[data-video-remote]');
    var callId = null;
    var startedBy = null;
    var callStatus = null;
    var lastSignalId = 0;
    var peer = null;
    var localStream = null;
    var pendingOffer = null;
    var pendingCandidates = [];
    var polling = false;
    var appointmentCompleted = false;

    function setState(message) { state.textContent = message; }
    function setError(message) { error.textContent = message || ''; }

    function post(action, values) {
      var data = new URLSearchParams();
      data.append('csrfToken', csrf);
      data.append('appointmentId', appointment);
      data.append('action', action);
      Object.keys(values || {}).forEach(function (key) { data.append(key, values[key]); });
      return window.fetch(endpoint, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', Accept: 'application/json' }, body: data.toString() })
        .then(function (response) { return response.json().catch(function () { return {}; }).then(function (body) { if (!response.ok) throw new Error(body.error || 'The video call could not be updated.'); return body; }); });
    }

    function enableCallControls(enabled) {
      endButton.hidden = !enabled;
      micButton.hidden = !enabled;
      cameraButton.hidden = !enabled;
    }

    function closeMedia() {
      if (peer) { peer.onicecandidate = null; peer.ontrack = null; peer.close(); peer = null; }
      if (localStream) { localStream.getTracks().forEach(function (track) { track.stop(); }); localStream = null; }
      localVideo.srcObject = null;
      remoteVideo.srcObject = null;
      pendingCandidates = [];
      pendingOffer = null;
      micButton.textContent = 'Mute microphone';
      cameraButton.textContent = 'Turn camera off';
      enableCallControls(false);
    }

    function closeCompletedAppointment() {
      appointmentCompleted = true;
      closeMedia();
      callId = null; startedBy = null; callStatus = null;
      startButton.hidden = true; startButton.disabled = true;
      acceptButton.hidden = true; endButton.hidden = true;
      setState('This appointment is complete. Book a new appointment to start another consultation.');
    }

    function getMedia() {
      if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        throw new Error('Video calls require HTTPS, or localhost during development, and a browser with camera support.');
      }
      return navigator.mediaDevices.getUserMedia({ audio: true, video: { facingMode: 'user' } }).then(function (stream) {
        localStream = stream;
        localVideo.srcObject = stream;
        enableCallControls(true);
        return stream;
      });
    }

    function sendSignal(type, value) {
      return post('signal', { callId: callId, type: type, payload: JSON.stringify(value) });
    }

    function createPeer() {
      if (peer) return peer;
      peer = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] });
      localStream.getTracks().forEach(function (track) { peer.addTrack(track, localStream); });
      peer.ontrack = function (event) { if (event.streams && event.streams[0]) remoteVideo.srcObject = event.streams[0]; };
      peer.onicecandidate = function (event) {
        if (event.candidate && callId) sendSignal('ICE', event.candidate.toJSON ? event.candidate.toJSON() : event.candidate).catch(function (problem) { setError(problem.message); });
      };
      peer.onconnectionstatechange = function () {
        if (!peer) return;
        if (peer.connectionState === 'connected') setState('Video connected. Keep this page open for the appointment.');
        else if (peer.connectionState === 'failed') setState('The network could not connect this call. Try again on another network.');
        else if (peer.connectionState === 'disconnected') setState('Video connection interrupted. Trying to reconnect…');
      };
      return peer;
    }

    function flushCandidates() {
      if (!peer || !peer.remoteDescription) return Promise.resolve();
      var queued = pendingCandidates.splice(0);
      return queued.reduce(function (chain, candidate) {
        return chain.then(function () { return peer.addIceCandidate(new RTCIceCandidate(candidate)); });
      }, Promise.resolve());
    }

    function receiveCandidate(payload) {
      var candidate = JSON.parse(payload);
      if (!peer || !peer.remoteDescription) { pendingCandidates.push(candidate); return Promise.resolve(); }
      return peer.addIceCandidate(new RTCIceCandidate(candidate));
    }

    function handleOffer(payload) {
      pendingOffer = JSON.parse(payload);
      acceptButton.hidden = false;
      startButton.hidden = true;
      setState('Incoming video call. Accept when you are ready.');
    }

    function handleSignal(signal) {
      if (signal.type === 'OFFER' && startedBy !== userId) { handleOffer(signal.payload); return Promise.resolve(); }
      if (signal.type === 'ANSWER' && startedBy === userId && peer) {
        return peer.setRemoteDescription(new RTCSessionDescription(JSON.parse(signal.payload))).then(flushCandidates);
      }
      if (signal.type === 'ICE') return receiveCandidate(signal.payload);
      return Promise.resolve();
    }

    function applyCall(call) {
      if (!call) return;
      if (call.status === 'ENDED') {
        if (callId === Number(call.id)) closeMedia();
        callId = null; startedBy = null; callStatus = null;
        acceptButton.hidden = true; startButton.hidden = false;
        setState('The video call has ended.');
        return;
      }
      var id = Number(call.id);
      if (callId !== id) { closeMedia(); callId = id; lastSignalId = 0; }
      startedBy = Number(call.startedBy); callStatus = call.status;
      if (startedBy !== userId && call.status === 'RINGING') {
        startButton.hidden = true;
        setState('The other participant is calling. Waiting for the call offer…');
      } else if (call.status === 'ACTIVE' && !peer) {
        startButton.hidden = true;
        endButton.hidden = false;
        setState('This call is active in the other participant’s browser. If you reloaded this page, end it and start a new call.');
      } else if (startedBy === userId && call.status === 'RINGING') {
        startButton.hidden = true;
        setState('Calling the other participant…');
      }
    }

    function poll() {
      if (polling) return;
      polling = true;
      window.fetch(endpoint + '?appointmentId=' + encodeURIComponent(appointment) + '&after=' + encodeURIComponent(lastSignalId), { credentials: 'same-origin', headers: { Accept: 'application/json' } })
        .then(function (response) { return response.json().catch(function () { return {}; }).then(function (body) { if (!response.ok) throw new Error(body.error || 'Call status could not be refreshed.'); return body; }); })
        .then(function (body) {
          if (body.appointmentStatus !== 'CONFIRMED') {
            applyCall(body.call);
            closeCompletedAppointment();
            if (userRole === 'PATIENT' && patientAppointmentsUrl) window.location.replace(patientAppointmentsUrl);
            return;
          }
          applyCall(body.call);
          return (body.signals || []).reduce(function (chain, signal) {
            return chain.then(function () { lastSignalId = Math.max(lastSignalId, Number(signal.id)); return handleSignal(signal); });
          }, Promise.resolve());
        }).catch(function (problem) { setError(problem.message); }).finally(function () { polling = false; });
    }

    startButton.addEventListener('click', function () {
      if (appointmentCompleted) return;
      startButton.disabled = true; setError('');
      post('start').then(function (call) {
        applyCall(call);
        if (Number(call.startedBy) !== userId || call.status !== 'RINGING') return;
        if (!call.created) { setState('A call started earlier in another page. End it and start a new call to reconnect.'); endButton.hidden = false; return; }
        return getMedia().then(function () {
          var connection = createPeer();
          return connection.createOffer().then(function (offer) { return connection.setLocalDescription(offer); }).then(function () {
            return sendSignal('OFFER', { type: peer.localDescription.type, sdp: peer.localDescription.sdp });
          }).then(function () { setState('Calling… waiting for the other participant to accept.'); });
        });
      }).catch(function (problem) {
        setError(problem.message);
        if (/appointment is no longer active/i.test(problem.message)) { closeCompletedAppointment(); return; }
        if (callId) post('end', { callId: callId }).catch(function () {});
        closeMedia(); callId = null; startedBy = null; callStatus = null; startButton.hidden = false; setState('Call could not start. Check camera and microphone access, then try again.');
      }).finally(function () { startButton.disabled = false; });
    });

    acceptButton.addEventListener('click', function () {
      if (appointmentCompleted) return;
      acceptButton.disabled = true; setError('');
      if (!pendingOffer || !callId) { setError('The call offer has not arrived yet.'); acceptButton.disabled = false; return; }
      getMedia().then(function () {
        var connection = createPeer();
        return connection.setRemoteDescription(new RTCSessionDescription(pendingOffer)).then(function () { return post('accept', { callId: callId }); }).then(flushCandidates)
          .then(function () { return connection.createAnswer(); }).then(function (answer) { return connection.setLocalDescription(answer); }).then(function () {
            return sendSignal('ANSWER', { type: peer.localDescription.type, sdp: peer.localDescription.sdp });
          }).then(function () { acceptButton.hidden = true; setState('Connecting video…'); });
      }).catch(function (problem) { setError(problem.message); closeMedia(); if (/appointment is no longer active/i.test(problem.message)) closeCompletedAppointment(); }).finally(function () { acceptButton.disabled = false; });
    });

    endButton.addEventListener('click', function () {
      var ending = callId ? post('end', { callId: callId }) : Promise.resolve();
      ending.catch(function (problem) { setError(problem.message); }).finally(function () {
        closeMedia(); callId = null; startedBy = null; callStatus = null; acceptButton.hidden = true; startButton.hidden = false; setState('The video call has ended.');
      });
    });

    micButton.addEventListener('click', function () {
      if (!localStream) return;
      var track = localStream.getAudioTracks()[0]; if (!track) return;
      track.enabled = !track.enabled; micButton.textContent = track.enabled ? 'Mute microphone' : 'Unmute microphone';
    });
    cameraButton.addEventListener('click', function () {
      if (!localStream) return;
      var track = localStream.getVideoTracks()[0]; if (!track) return;
      track.enabled = !track.enabled; cameraButton.textContent = track.enabled ? 'Turn camera off' : 'Turn camera on';
    });

    window.addEventListener('pagehide', function () {
      if (!callId || !navigator.sendBeacon) return;
      var data = new URLSearchParams(); data.append('csrfToken', csrf); data.append('appointmentId', appointment); data.append('callId', callId); data.append('action', 'end');
      navigator.sendBeacon(endpoint, new Blob([data.toString()], { type: 'application/x-www-form-urlencoded;charset=UTF-8' }));
    });

    poll();
    window.setInterval(poll, 1800);
  }());

  var chat = document.querySelector('.chat-card');
  if (!chat) return;
  var appointment = chat.getAttribute('data-appointment');
  var endpoint = chat.getAttribute('data-endpoint');
  var panel = chat.querySelector('.chat-messages');
  var error = chat.querySelector('.chat-error');
  var form = chat.querySelector('.chat-form');
  var lastJson = '';

  function refresh() {
    return window.fetch(endpoint + '?appointment=' + encodeURIComponent(appointment), {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    }).then(function (response) {
      if (!response.ok) throw new Error('Chat could not be refreshed.');
      return response.json();
    }).then(function (messages) {
      var serialized = JSON.stringify(messages);
      if (serialized === lastJson) return;
      lastJson = serialized;
      panel.replaceChildren();
      if (!messages.length) {
        var empty = document.createElement('div');
        empty.className = 'chat-empty';
        empty.textContent = 'No messages yet. Start the conversation here.';
        panel.append(empty);
      }
      messages.forEach(function (item) {
        var bubble = document.createElement('article');
        bubble.className = 'chat-bubble';
        var head = document.createElement('div');
        head.className = 'chat-bubble-head';
        var name = document.createElement('strong');
        name.textContent = item.sender;
        var time = document.createElement('time');
        time.textContent = item.sentAt;
        var body = document.createElement('p');
        body.textContent = item.message;
        head.append(name, time);
        bubble.append(head, body);
        panel.append(bubble);
      });
      panel.scrollTop = panel.scrollHeight;
      error.textContent = '';
    }).catch(function (problem) {
      error.textContent = problem.message;
    });
  }

  if (form) {
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      var input = form.elements.message;
      var message = input.value.trim();
      if (!message) return;
      var data = new URLSearchParams();
      data.append('appointment', appointment);
      data.append('message', message);
      data.append('csrfToken', chat.getAttribute('data-csrf'));
      window.fetch(endpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
          Accept: 'application/json'
        },
        body: data
      }).then(function (response) {
        if (!response.ok) {
          return response.text().then(function (text) {
            throw new Error(text || 'Message could not be sent.');
          });
        }
        input.value = '';
        return refresh();
      }).catch(function (problem) {
        error.textContent = problem.message;
      });
    });
  }

  refresh();
  window.setInterval(refresh, 5000);
}());
