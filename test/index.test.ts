// from the wonderully punny tutorial
// https://levelup.gitconnected.com/how-to-unit-test-html-and-vanilla-javascript-without-a-ui-framework-c4c89c9f5e56
// https://xa1.at/jest-plain-webpage/

import { enableFetchMocks } from 'jest-fetch-mock'
enableFetchMocks()
//import { mocked } from 'ts-jest/utils'
let fetchAny = fetch as any;

import { fireEvent, getByText, findByText, queryByText } from '@testing-library/dom'
import '@testing-library/jest-dom/extend-expect'
import { JSDOM } from 'jsdom'
import {jest, describe, expect, it, beforeEach} from '@jest/globals'
import fs from 'fs'
import path from 'path'

// files
const html = fs.readFileSync('src/ad.html', 'utf8');
// NB. order on require is important! must match export
const [add_consent, taskCompleteCode, append_play_link, change_to_play_button, get_anchor, url_params, consent_form_clicked, task_popup_window] = require('../src/mturk.js')


let dom : JSDOM
let body : HTMLElement 

describe('index.html', () => {
  beforeEach(() => {
    // do what the index page says. dangerous if we dont trust ourselves
    dom = new JSDOM(html, { runScripts: 'dangerously' })
    body = dom.window.document.body
    dom.reconfigure({
       url: 'https://localhost?workerId=wid&hitId=hid&assignmentId=aid',
    })
    global.document = dom.window.document
    global.window = dom.window

    //mocked(fetch).mockReturnValueOnce(Promise.resolve({ anchor: "testanchor" }));
    //fetch.mockResponse(req=> Promise.resolve({anchor: "anc"}))
    //mocked(get_anchor).mockReturnValue({anchor: "testanchor"}) 
    fetchAny.mockResponse('{"anchor": "anc"}')
})

  it('params mocked', () => {
     const m = url_params();
     expect(m).toHaveProperty('id', 'wid')
  })
  it('params mocked. updated url', () => {
     dom.reconfigure({ url: 'https://localhost/'})
     const m = url_params();
     expect(m).toBeUndefined()
  })
  it('no consent when url is missing assignmentId', () => {
    // no more params
    dom.reconfigure({ url: 'https://localhost/'})
    add_consent()
    expect(getByText(body, 'Play a game for science!')).not.toBeNull()
    expect(queryByText(body, 'Read Consent')).toBeNull()
  })

  it('consent with url change', () => {
    add_consent()
    expect(queryByText(body, 'Play a game for science!')).toBeNull()
    expect(getByText(body, 'Read Consent')).not.toBeNull()
  })
  it('consent -> accept', async () => {
    add_consent()
    const consent = getByText(body, "Read Consent") 
    // fireEvent.click(consent) // doesn't work as expected
    expect(consent).not.toBeNull()
    consent_form_clicked()
    const play = await findByText(body,"PLAY!")
    expect(play).not.toBeNull()
  })

  /* 
   //window.open not implemented by jest-dom
   //TODO: fix mock
  it('consent -> decline', async () => {
    global.open = jest.fn(function(url : URL, _target : string, _feature : string ){
       const h = fs.readFileSync('src/' + url, 'utf8');
       let d = new JSDOM(h, { runScripts: 'dangerously' })
       d.parent = dom
       return(d)
    })
    add_consent()
    consent_form_clicked()
    const play = await findByText(body,"PLAY!")
    const popup = task_popup_window()
    console.log(popup)
    expect(popup).not.toBeNull()
    //find /NOT/ in popup
    // push to change body text
  })
  /* */
})
