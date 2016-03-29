import { expect } from 'chai';

import { getComponentUrl, getComponentIssuesUrl, getComponentDrilldownUrl } from '../../src/main/js/helpers/urls';


const SIMPLE_COMPONENT_KEY = 'sonarqube';
const COMPLEX_COMPONENT_KEY = 'org.sonarsource.sonarqube:sonarqube';
const COMPLEX_COMPONENT_KEY_ENCODED = encodeURIComponent(COMPLEX_COMPONENT_KEY);
const METRIC = 'coverage';
const PERIOD = '3';


describe('URLs', function () {
  var oldBaseUrl;

  beforeEach(function () {
    oldBaseUrl = window.baseUrl;
  });

  afterEach(function () {
    window.baseUrl = oldBaseUrl;
  });

  describe('#getComponentUrl', function () {
    it('should return component url', function () {
      expect(getComponentUrl(SIMPLE_COMPONENT_KEY)).to.equal('/dashboard?id=' + SIMPLE_COMPONENT_KEY);
    });

    it('should encode component key', function () {
      expect(getComponentUrl(COMPLEX_COMPONENT_KEY)).to.equal('/dashboard?id=' + COMPLEX_COMPONENT_KEY_ENCODED);
    });
  });

  describe('#getComponentIssuesUrl', function () {
    it('should work without parameters', function () {
      expect(getComponentIssuesUrl(SIMPLE_COMPONENT_KEY, {})).to.equal(
          '/component_issues?id=' + SIMPLE_COMPONENT_KEY + '#');
    });

    it('should encode component key', function () {
      expect(getComponentIssuesUrl(COMPLEX_COMPONENT_KEY, {})).to.equal(
          '/component_issues?id=' + COMPLEX_COMPONENT_KEY_ENCODED + '#');
    });

    it('should work with parameters', function () {
      expect(getComponentIssuesUrl(SIMPLE_COMPONENT_KEY, { resolved: 'false' })).to.equal(
          '/component_issues?id=' + SIMPLE_COMPONENT_KEY + '#resolved=false');
    });

    it('should encode parameters', function () {
      expect(getComponentIssuesUrl(SIMPLE_COMPONENT_KEY, { componentUuids: COMPLEX_COMPONENT_KEY })).to.equal(
          '/component_issues?id=' + SIMPLE_COMPONENT_KEY + '#componentUuids=' + COMPLEX_COMPONENT_KEY_ENCODED);
    });
  });

  describe('#getComponentDrilldownUrl', function () {
    it('should return component drilldown url', function () {
      expect(getComponentDrilldownUrl(SIMPLE_COMPONENT_KEY, METRIC)).to.equal(
          '/component_measures/metric/' + METRIC + '?id=' + SIMPLE_COMPONENT_KEY);
    });

    it('should encode component key', function () {
      expect(getComponentDrilldownUrl(COMPLEX_COMPONENT_KEY, METRIC)).to.equal(
          '/component_measures/metric/' + METRIC + '?id=' + COMPLEX_COMPONENT_KEY_ENCODED);
    });
  });
});